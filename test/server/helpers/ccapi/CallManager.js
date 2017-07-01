"use strict";
var _ = require("underscore");
var request = require("request-promise");
// var cloudTools = require("CloudTools");
var objectid = require("objectid");
var Tasks = require("./Tasks");
var URL = require("url");
var assert = require("assert");
var xml2js = require("xml2js");

var parseString = new xml2js.Parser({
  trim: true,
  explicitRoot: false,
  explicitArray: false,
  mergeAttrs: true,
  explicitChildren: true,
  preserveChildrenOrder: true,
  charkey: "content",

}).parseString;

var calls = {};
var options;

var relevantCallAttrs = [
  "call_id",
  "account_id",
  "application_id",
  "url", "to", "from",
  "timeout", "callstatus_url",
];
var callbackAttrs = ["call_id", "account_id", "application_id", "to", "from", "status", "cause"];

// Quand le module est initialisé je peux récupérer la conf et le loggerManager
// cloudTools.initializeModule.addListener(function () {
//      logger = cloudTools.loggerManager.getLogger("CCAPISimulator.CallManager");
//      options = cloudTools.configService.getConfig().ccapi_mock;
// });

module.exports.options = (opt) => {
  options = opt;
};

module.exports.clearCalls = function () {
  calls = {};
};

module.exports.getCall = function (callid) {
  return calls[callid];
};

module.exports.createCall = function (params, done) {
  console.log("createCall", params);

  var call = _.pick(params, relevantCallAttrs);
  call.call_id = objectid().toString();
  calls[call.call_id] = call;

  /* Cas ou ccapi retourne directement une erreur */
  if (call.to === "errCallCcapi") {
    var err = new Error("Erreur Appel CCapi");
    err.name = "errorCallCcapi";
    err.statusCode = 400;
    return done(err);
  }

  done(null, call);

  call.tasks = new Tasks()
    .addTask(function (cbk) {
      call.status = "ringing";
      callStatus(call, cbk);
    }, "STATUS:ringing")
    .addDelay(ringTime(call.to));


  if (call.to === "canceledSDA") {
    call.tasks
      .addTask(function (cbk) {
        call.status = "canceled";
        call.cause = "CAU_NO_USER_RES";
        delete calls[call.call_id];
        callStatus(call, cbk);
      });
  } else {
    call.tasks
      .addTask(function (cbk) {
        call.status = "in-progress";
        callStatus(call, cbk);
      }, "STATUS:in-progress")
      .addTask(function (cbk) {
        loadCallML(call, cbk);
      }, "loadCallML")
      .addTask(function (cbk) {
        playCallMLVerbs(call, cbk);
      }, "PLAY_XML");

  }
};

module.exports.updateCall = function (params, done) {
  console.log("updateCall", params);
  var call = calls[params.call_id];
  if (!call) {
    return done(new Error(`le call ${ params.call_id } n'existe pas ou pluss`));
  }

  var oldUrl = call.url;
  var newCall = _.pick(params, relevantCallAttrs);
  _.extend(call, newCall);
  done(null, call);
  if (params.status === "completed") {
    console.log("arret de l appel");
    call.tasks
      .clearTasks()
      .addTask(function (cbk) {
        _.extend(call, {
          cause: "rest-hangup",
          status: "completed",
        });
        delete calls[call.call_id];
        return callStatus(call, cbk);
      }, "REST_HANGUP");
    return;
  }
  console.log("oldUrl:", oldUrl);


  if (params.url && params.url !== oldUrl) {
    call.url = URL.resolve(oldUrl, call.url);
    console.log("reload du call");
    call.tasks
      .clearTasks()
      .addTask(function (cbk) {
        loadCallML(call, cbk);
      }, "REST_REDIRECT:fetchCallML")
      .addTask(function (cbk) {
        playCallMLVerbs(call, cbk);
      }, "REST_REDIRECT:PLAY XML");
  }
};

module.exports.dialLegBHangUp = function (params, done) {
  console.log("dialLegBHangUp", params);
  done();
  var call = calls[params.call_id];
  call.tasks
    .clearTasks()
    .addTask(function (cbk) {
      _.extend(call.dial, {
        dialstatus: "completed",
        bridgestatus: "completed",
        bridgecause: "hangup-b",
        bridgeduration: 42,
      });
      callDialStatus(call, cbk);
    }, "dialStatus:LegBHangUp");

  redirectDialCallback(call);
};


module.exports.userHangUp = function (params, done) {
  console.log("userHangUp", params);
  done();
  var call = calls[params.call_id];
  call.tasks
    .clearTasks()
    .addTask(function (cbk) {
      if (!call.dial) {
        return cbk();
      }
      _.extend(call.dial, {
        dialstatus: "completed",
        bridgestatus: "completed",
        bridgecause: "hangup-a",
        bridgeduration: 42,
      });
      callDialStatus(call, cbk);
    })
    .addTask(function (cbk) {
      _.extend(call, {
        cause: "user-hangup",
        status: "completed",
      });
      delete calls[call.call_id];
      return callStatus(call, cbk);
    }, "USER_HANGUP");
};

//send status change evt
var callStatus = function (call, cbk) {
  console.log("callStatus", call.call_id, call.status);
  if (!call.callstatus_url) {
    return cbk();
  }
  var callAttrs = _.pick(call, callbackAttrs);
  request.post({
    uri: call.callstatus_url,
    form: callAttrs,
  }).then(function () {
    console.log("callStatus fetched OK");
    cbk();
  }).catch(cbk);

};

//sendDialStatus
var callDialStatus = function (call, cbk) {
  console.log("callDialStatus", call.call_id, call.status, call.dial);
  if (!call.dial.ml.statusurl) {
    return cbk();
  }

  var callAttrs = _.pick(call, callbackAttrs);
  _.extend(callAttrs, _.pick(call.dial,
                             "dialstatus", "bridgestatus", "bridgecause", "bridgeduration"));

  var uri = URL.resolve(call.url, call.dial.ml.statusurl);
  request.post({
    uri: uri,
    form: callAttrs,
  }).then(function () {
    console.log("callDialStatus fetched OK");
    cbk();
  }).catch(cbk);

};


var loadCallML = function (call, cbk) {
  fetchCallML("", call, cbk);
};

var fetchCallMLReq = function (uri, callAttrs) {
  console.log("fetchCallML on", uri, "for:", callAttrs);
  return request.post({
    uri: uri,
    form: callAttrs})
    .then(function (body) {
      return new Promise(function (resolve, reject) {
        parseString(body, function (err, result) {
          if (err) {
            return reject(err);
          }
          var res = _.chain(result)
              .omit("#name")
            .mapObject(function (value) {
              return _.omit(value, "#name");
            })
            .value();
          res._uri = uri;
          resolve(res);
        });
      });
    });
};

var fetchDialCallbackML = function (call, cbk) {
  var uri = URL.resolve(call.dial.ml._uri, call.dial.ml.callbackurl);
  var callAttrs = _.pick(call, callbackAttrs);
  _.extend(callAttrs, _.pick(call.dial,
                             "dialstatus", "bridgestatus", "bridgecause", "bridgeduration"));

  fetchCallMLReq(uri, callAttrs)
    .then(function (res) {
      console.log("loadDialCallbackML fetched OK", res);
      call.ml = res;
      cbk();
    }).catch(cbk);
};

var fetchCallML = function (url, call, cbk) {
  var uri = URL.resolve(call.url, url);
  var callAttrs = _.pick(call, callbackAttrs);
  fetchCallMLReq(uri, callAttrs)
    .then(function (res) {
      console.log("loadCallML fetched OK", res);
      call.ml = res;
      cbk();
    }).catch(cbk);
};

var ringTime = function (phoneNum) {
  var ringingTime = options.ringingTime;
  try {
    ringingTime = parseInt(phoneNum.split("_")[2], 10);
  } catch (exeption) {
    ringingTime = options.ringingTime;
  }
  console.log("temps de sonnerie", ringingTime);
  return ringingTime;
};

var redirectDialCallback = function (call) {
  call.tasks
    .addTask(function (cbk) {
      //ATTENTION, tous les dials sont sensés avoir un callbackurl!!
      fetchDialCallbackML(call, cbk);
      delete call.dial;
    }, "DIAL_CALLBACK_URL:fetchDialCallbackML")
    .addTask(function (cbk) {
      playCallMLVerbs(call, cbk);
    }, "DIAL_CALLBACK_URL:PLAY XML");
};

var playCallMLVerbs = function (call, done) {
  console.log("playCallMLVerbs", _.keys(call.ml));
  call.tasks.clearTasks();
  var lastVerb;
  _.each(call.ml, function (value, key) {
    if (key === "_uri") {
      return;
    }
    console.log("========>", key, value);
    lastVerb = key;
    switch (key) {
    case "Play":
      if (value.loop === 0) {
        //joue indifinement un son: ne rien faire
        console.log("play sound indifinement", call);
      } else {
        call.tasks.addDelay(options.playingTime, "PLAY_SOUND");
      }
      break;
    case "Redirect":
      call.tasks
        .addTask(function (cbk) {
          fetchCallML(value.content, call, cbk);
        }, "REDIRECT:fetchCallML")
        .addTask(function (cbk) {
          playCallMLVerbs(call, cbk);
        }, "REDIRECT:PLAY XML");
      break;
    case "Record":
      call.tasks
        .addDelay(options.recordTime, "RECORDING")
        .addTask(function (cbk) {
          var uri = URL.resolve(call.ml._uri, value.callbackurl);
          var callAttrs = _.pick(call, callbackAttrs);
          var digit = call.to === "recordEnd0" ? value.finishonkey[0] :
            value.finishonkey[1];
          _.extend(callAttrs, {
            record_cause: "digit-a",
            record_url: "ccapi://Record/record_id",
            record_digits: digit,
          });
          fetchCallMLReq(uri, callAttrs)
            .then(function (res) {
              console.log("loadRecordCallbackML fetched OK", res);
              call.ml = res;
              cbk();
            }).catch(cbk);
        }, "CALLBACK_RECORD:fetchCallML")
        .addTask(function (cbk) {
          playCallMLVerbs(call, cbk);
        }, "PLAY CALLBACK_RECORD XML");
      break;
    case "HangUp":
      call.tasks
        .addTask(function (cbk) {
          _.extend(call, {
            cause: "xml-hangup",
            status: "completed",
          });
          delete calls[call.call_id];
          return callStatus(call, cbk);
        }, "HangUp status");
      break;
    case "Dial" : //gestion partiel d un dial Number...
      //autorise uniquement le waiting avec un Play:
      call.tasks
        .addTask(function (cbk) {
          //pas de suite dans un Dial:
          call.dial = {ml: value};
          call.dial.ml._uri = call.ml._uri;
          delete call.ml;
          fetchCallML(value.waitingurl, call, cbk);
        }, "DIAL:fetchCallML:waitingurl")
        .addTask(function (cbk) {
          console.log("===*******>waitingml", call.ml);
          assert.ok(call.ml.Play, "le scenario d attente doit contenir un Play");
          delete call.ml.Play;
          delete call.ml._uri;
          assert.deepEqual(call.ml, {},
                           "le scenario d attente doit contenir uniquement Play");
          delete call.ml;
          cbk();
        }, "CHECK WAIT IS PLAY ONLY")
        .addDelay(ringTime(value.Number.content), "RING_DIAL_NUMBER");
      if (_.contains(["no-answerSDA", "busySDA"], value.Number.content)) {
        call.tasks
          .addTask(function (cbk) {
            call.dial.dialstatus = value.Number.content.substring(0,
                                                                  value.Number.content.length - 3);
            callDialStatus(call, cbk);
          }, "DIALSTATUS:no-answer");

        redirectDialCallback(call);

      } else {
        call.tasks
          .addTask(function (cbk) {
            call.dial.dialstatus = "in-progress";
            call.dial.bridgestatus = "in-progress";
            call.tasks.clearTasks();
            callDialStatus(call, cbk);
          }, "DIALSTATUS:in-progress");
      }
      break;
    }
  });

  if (_.contains(["Play"], lastVerb)) {
    //si le scenario fini, alors xml hangup
    //console.log('ajout XML end task')
    call.tasks
      .addTask(function (cbk) {
        //fin du scenario?
        _.extend(call, {
          cause: "xml-end",
          status: "completed",
        });
        delete calls[call.call_id];
        return callStatus(call, cbk);
      }, "XML_END");
  }
  done();
};
