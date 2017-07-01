"use strict";

const server = require("./helpers/server.js");
var ServerMock = require("./helpers/ccapi/server");
var callManagerMock = require("./helpers/ccapi/CallManager");
var request = require("request-promise");
var _ = require("underscore");
var TicketsEmitter = require("events");
var async = require("async");
var nock = require("nock");
var storeObjects = require("./helpers/Store");
var supertest = require("supertest");
require("chai").should();
const expect = require("chai").expect;
const sinon = require("sinon");

describe("test le dialogue avec CCAPI mock", function () {

  this.timeout(5000);
  var logger;
  var confServer = storeObjects.confServer;
  var serverMock; //mock ccapi
  var serverUrl = "http://localhost:8085/smartccivr";
  var ccapiUrl = "http://localhost:8086/ccapimock";
  var acdLinkUrl = `${confServer.environment.dispatch_url.internal }/smartccacdlink`;
  var ivrServicesUrl = `${confServer.environment.dispatch_url.internal }/smartccivrservices`;
  var tickets = new TicketsEmitter();

  var matchEvt = function (name, matching, callback) {
    tickets.once(name, function (evt) {
      console.log("matchEvt", evt, matching);
      expect(sinon.match(matching).test(evt))
        .to.equal(true, `wrong StatEvt_${ name}`);
      callback();
    });
  };
  var waitIncrDecrLimitter = function (callback) {
    _.delay(callback, 200);
  };
  var checkDurationFirstAction = function (evt) {
    evt.callTime.should.be.below(evt.time);
    var durationFromStart = evt.time - evt.callTime;
    evt.duration.should.be.within(durationFromStart - 500, durationFromStart);
  };

  beforeEach(function (done) {
    async.series([
      function (callback) {
        serverMock = new ServerMock(confServer, callback);
      },
      function (callback) {
        server.start(confServer)
          .then(() => callback(), callback);
      }, function (callback) {
        serverMock.start(callback);
      }, function (callback) {
        // msgBus = cloudTools.messageBus.getMessageBus();
        ivr.services.tickets.send_string = function (socket, string) {
          const evt = JSON.parse(string);
          console.log("IVR emitting", evt);
          if (evt.state && evt.nextState) {
            tickets.emit(`change_${ evt.state }_${ evt.nextState}`);
            tickets.emit(`evt_${ evt.state }_${ evt.nextState}`, evt);
          } else if (evt.action) {
            tickets.emit(`action_${ evt.action.type }_${ evt.action.name}`, evt);
          }
        };
        callback();
      },
    ], done);
  });
  // afterEach(function (done) {
  //   // CallStatus.getCalls().should.be.empty; //pas de fuite de memoire...
  //   done();
  // });

  afterEach(function (done) {
    async.series([
      function (callback) {
        server.stop();
        callback();
      },
      function (callback) {
        serverMock.stop(callback);
      },
      function (callback) {
        // server = null;
        serverMock = null;
        nock.cleanAll();
        callback();
      },
    ], done);
  });
  var msgBus;
  var call;
  var createInboundCall = function (scriptid, params) {
    var accountCallUrl = `${ccapiUrl }/Accounts/accountid1/Calls`;
    console.log("post on", accountCallUrl);
    var queryString = "";
    params = params || {};
    if (params.testFrom && params.testTo) {
      queryString = `?testFrom=${ params.testFrom }&testTo=${ params.testTo}`;
    }
    var start = params.start || "start";
    var callForm = {
      url: `${serverUrl }/script/${ scriptid }/node/${ start}`,
      callstatus_url: `${serverUrl }/script/${ scriptid }/status${ queryString}`,
      application_id: "appID",
      to: params.to || "+338AB",
      from: "0123456789",
      timeout: 4000,
    };
    return request.post(accountCallUrl, {
      form: callForm,
      json: true,
    }).then(function (body) {
      call = body;
      return body;
    });
  };
  var hangUpCaller = function () {
    var url = `${ccapiUrl }/callcontrol/${ call.call_id }/user_hangup`;
    console.log("post on", url);
    return request.post(url);
  };
  var updateCaller = function (url, callback) {
    callManagerMock.updateCall({
      call_id: call.call_id,
      url: url,
    }, callback);
  };
  var hangUpDialLegB = function () {
    var url = `${ccapiUrl }/callcontrol/${ call.call_id }/dial_leg_b_hangup`;
    console.log("post on", url);
    return request.post(url);
  };
  it("T01: doit simuler un appel avec 1 annonce suivi d une autre qui finit", function (done) {
    var lNock = storeObjects.nockStoreScript42();
    async.series([
      function (callback) {
        tickets.once("evt_Created_InProgress", function (evt) {
          //console.log('===evt', e)
          _.pick(evt, ["subject","accountid","applicationid","from","to","scriptid","state","nextState","duration"]).should.deep.equal({
            subject: "CALL",
            accountid: "accountid1",
            applicationid: "appID",
            from: "0123456789",
            to: "+338AB",
            scriptid: "script42",
            state: "Created",
            nextState: "InProgress",
            duration: 0,
          }, "wrong StatEvt");
          (evt.time).should.equal(evt.callTime);
          (_.now() - evt.time).should.be.below(500);
          callback();
        });
      },
      function (callback) {
        tickets.once("evt_InProgress_Terminated", function (evt) {
          _.pick(evt, ["subject","accountid","applicationid","from","to","scriptid","cause","ccapi_cause"]).should.deep.equal({
            subject: "CALL",
            accountid: "accountid1",
            applicationid: "appID",
            from: "0123456789",
            to: "+338AB",
            scriptid: "script42",
            cause: "IVR_HANG_UP",
            ccapi_cause: "xml-hangup",
          }, "wrong StatEvt");
          evt.duration.should.equal(evt.time - evt.callTime);
          callback();
        });
      },
      function (callback) {
        lNock.done();
        callback();
      },
    ], done);
    createInboundCall("script42");
  });
  it("T02: doit simuler un appel avec 1 annonce suivi d une autre qui boucle," +
     " puis l appelant raccroche", function (done) {
       var lNock = storeObjects.nockScript("script69");
       var lNockFiles = storeObjects.nockAllSounds();
       async.series([
         function (callback) {
           tickets.once("change_Created_InProgress", callback);
         },
         function (callback) {
           callback();
           hangUpCaller();
         },
         function (callback) {
           tickets.once("evt_InProgress_Terminated", function (evt) {
             _.pick(evt, ["from","to","cause"]).should.deep.equal({
               from: "0123456789",
               to: "+338AB",
               cause: "CALLER_HANG_UP",
             }, "wrong StatEvt");
             callback();
           });
         },
         function (callback) {
           lNock.done();
           lNockFiles.done();
           callback();
         },
       ], done);
       createInboundCall("script69", {
         testFrom: "testFrom",
         testTo: "testTo",
       }); //comme tous ceux qui bouclent finissent par 69.

     });
  it("T03: doit simuler un appel avec un transfert reussi et l appelant raccroche",
     function (done) {
       storeObjects.nockScript("script634");
       storeObjects.nockLimitManager();
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "0620870375",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_Transferred", {
             sda: "0620870375",
           }, callback);
         },
         function (callback) {
           setTimeout(callback, 200);
         },
         function (callback) {
           storeObjects.getCallOnSda("0620870375").should.equal(1);
           callback();
           hangUpCaller();
         },
         function (callback) {
           matchEvt("evt_Transferred_Terminated", {
             sda: "0620870375",
             bridgecause: "hangup-a",
             bridgeduration: sinon.match.string, //??
           }, callback);
         },
         function (callback) {
           setTimeout(callback, 200);
         },
         function (callback) {
           storeObjects.getCallOnSda("0620870375").should.equal(0);
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script634");
     });

  it("T04: doit simuler un appel avec un transfert reussi et le consultant raccroche",
     function (done) {
       storeObjects.nockAllScripts();
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "0620870375",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_Transferred", {
             sda: "0620870375",
           }, callback);
         },
         function (callback) {
           callback();
           hangUpDialLegB();
         },
         function (callback) {
           matchEvt("evt_Transferred_Terminated", {
             sda: "0620870375",
             bridgecause: "hangup-b",
             bridgeduration: sinon.match.string, //??
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script634");
     });

  it("T05: doit simuler un appel avec un transfert no answer, " +
     "debordement sur annonce, puis dissuasion IVR", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "no-answerSDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_InProgress", {
             failedSda: "no-answerSDA",
             dialcause: "no-answer",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_InProgress_Terminated", {
             cause: "IVR_HANG_UP",
             ccapi_cause: "xml-hangup",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script634_2");
     });

  it("T06: doit simuler un appel avec un abandon sur sonnerie", function (done) {
    storeObjects.nockAllScripts();
    storeObjects.nockAllSounds();
    async.series([
      function (callback) {
        matchEvt("evt_Created_TransferRinging", {
          ringingSda: "no-answerSDA",
        }, callback);
      },
      function (callback) {
        hangUpCaller();
        matchEvt("evt_TransferRinging_Terminated", {
          ringingSda: "no-answerSDA",
          cause: "CALLER_HANG_UP",
        }, callback);
      },
      function (callback) {
        //attendre tous les status CCAPI
        _.delay(callback, 100);
      },
    ], done);
    createInboundCall("script634_2");
  });

  it("T07: doit simuler un appel avec un transfert vers un numéro busy, " +
     "sans debordement du coup dissuasion IVR", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "busySDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_Terminated", {
             cause: "IVR_HANG_UP",
             failedSda: "busySDA",
             dialcause: "busy",
             ccapi_cause: "xml-hangup",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script634_3");
     });

  it("T08: doit simuler un appel avec un transfert busy, " +
     "debordement sur annonce, puis abandon pdt l annonce", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "busySDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_InProgress", {
             failedSda: "busySDA",
             dialcause: "busy",
           }, callback);
         },
         function (callback) {
           hangUpCaller();
           matchEvt("evt_InProgress_Terminated", {
             cause: "CALLER_HANG_UP",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script634_4");
     });

  it("T09: doit simuler un appel avec un transfert busy, " +
     "debordement sur un transfert no answer, dissuasion IVR", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "busySDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_TransferRinging", {
             failedSda: "busySDA",
             dialcause: "busy",
             ringingSda: "no-answerSDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_Terminated", {
             failedSda: "no-answerSDA",
             dialcause: "no-answer",
             cause: "IVR_HANG_UP",
             ccapi_cause: "xml-hangup",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script634_5");
     });

  it("T10 : doit simuler un appel avec abadon pdt la sonnerie(fuite de memoire)",
     function (done) {
       storeObjects.nockStoreScript42();
       async.series([
         function (callback) {
           //attendre l annulation de l appel
           _.delay(callback, 100);
         },
         function (callback) {
           // CallStatus.getCalls().should.be.empty; //pas de fuite de memoire...
           callback();
         },
       ], done);
       createInboundCall("script42", {to: "canceledSDA"});
     });
  var nockAcdLinkEnqueue = function () {
    return nock(`${acdLinkUrl }/call/`)
      .log(console.log)
      .post(/.*enqueue$/)
      .reply(200, {
        waitSound: "waitsound",
      });
  };

  it("T11: doit simuler un appel transfert sur file réussi, puis abandon pdt l attente",
     function (done) {
       var nock2 = nockAcdLinkEnqueue();
       var nock3;
       storeObjects.nockScript("script656");
       async.series([
         function (callback) {
           matchEvt("evt_Created_AcdTransferred", {
             queueid: "ma_queue_id",
           }, callback);
         },
         function (callback) {
           nock3 = nock(acdLinkUrl)
             .log(console.log)
             .post(`/call/${ call.call_id }/principal/status`,
                   function (body) {
                     sinon.match({
                       call_id: call.call_id,
                       account_id: call.account_id,
                       status: "completed",
                       IVRStatus: {
                         state: "Terminated",
                         lastChange: sinon.match.number,
                       },
                     }).test(body).should.equal(true);
                     return true;
                   })
             .reply(204);
           hangUpCaller();
           matchEvt("evt_AcdTransferred_Terminated", {
             acdcause: undefined,
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
         function (callback) {
           nock2.done();
           nock3.done();
           callback();
         },
       ], done);
       createInboundCall("script656");
     });

  it("TTrQ2: doit simuler un appel transfert sur file échec technique mise en file",
     function (done) {
       var nock2 = nock(`${acdLinkUrl }/call/`)
           .log(console.log)
         .post(/.*enqueue$/)
         .reply(500, {
           status: 500,
         });

       storeObjects.nockScript("script656");
       async.series([
         function (callback) {
           matchEvt("evt_Created_AcdTransferred", {
             queueid: "ma_queue_id",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_AcdTransferred_Terminated", {
             queueid: undefined,
             cause: undefined,
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
         function (callback) {
           nock2.done();
           callback();
         },
       ], done);
       createInboundCall("script656");
     });

  it("T12_Timeout: doit simuler un appel transfert sur file echec " +
     "car timeout et debordement sur un son", function (done) {
       var urlFallback, nock3;
       storeObjects.nockAllSounds();
       storeObjects.nockScript("script656_3");
       var nock2 = nock(`${acdLinkUrl }/call/`)
         .log(console.log)
         .post(/.*enqueue$/, function (body) {
           sinon.match({
             call_id: call.call_id,
             account_id: call.account_id,
             application_id: call.application_id,
             to: call.to,
             from: call.from,
             queue_id: "queue_id2",
             callTime: sinon.match.number,
             ivr_fallback: sinon.match(function (fallback) {
               urlFallback = fallback;
               return _.isString(fallback);
             }),
           }).test(body).should.equal(true);
           return true;
         })
         .reply(200, {
           waitSound: "waitsound",
         });
       async.series([
         function (callback) {
           matchEvt("evt_Created_AcdTransferred", {
             queueid: "queue_id2",
           }, callback);
         },
         function (callback) {
           nock3 = nock(acdLinkUrl)
             .log(console.log)
             .post(`/call/${ call.call_id }/principal/status`,
                   function (body) {
                     sinon.match({
                       call_id: call.call_id,
                       account_id: call.account_id,
                       status: "in-progress",
                       IVRStatus: {
                         state: "InProgress",
                         //pour ne pas retoucher AcdLink
                         lastChange: sinon.match.number,
                       },
                     }).test(body).should.equal(true);
                     return true;
                   })
             .reply(204);
           //acdLink debordement:
           updateCaller(`${urlFallback }?overflowcause=QUEUE_TIMEOUT`, callback);
         },
         function (callback) {
           matchEvt("evt_AcdTransferred_InProgress", {
             acdcause: "ACD_OVERFLOW",
             overflowcause: "QUEUE_TIMEOUT",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_InProgress_Terminated", {
             cause: "IVR_HANG_UP",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
         function (callback) {
           nock2.done();
           nock3.done();
           callback();
         },
       ], done);
       createInboundCall("script656_3");
     });

  it("T12: doit simuler un appel transfert sur file echec " +
     "car no_agent et debordement sur un son", function (done) {
       var urlFallback, nock3;
       storeObjects.nockAllSounds();
       storeObjects.nockScript("script656_2");
       var nock2 = nock(`${acdLinkUrl }/call/`)
         .log(console.log)
         .post(/.*enqueue$/, function (body) {
           sinon.match({
             call_id: call.call_id,
             account_id: call.account_id,
             application_id: call.application_id,
             to: call.to,
             from: call.from,
             queue_id: "queue_id2",
             callTime: sinon.match.number,
             ivr_fallback: sinon.match(function (fallback) {
               urlFallback = fallback;
               return _.isString(fallback);
             }),
           }).test(body).should.equal(true);
           return true;
         })
         .reply(200, {
           waitSound: "waitsound",
         });
       async.series([
         function (callback) {
           matchEvt("evt_Created_AcdTransferred", {
             queueid: "queue_id2",
           }, callback);
         },
         function (callback) {
           nock3 = nock(acdLinkUrl)
             .log(console.log)
             .post(`/call/${ call.call_id }/principal/status`,
                   function (body) {
                     sinon.match({
                       call_id: call.call_id,
                       account_id: call.account_id,
                       status: "in-progress",
                       IVRStatus: {
                         state: "InProgress",
                         lastChange: sinon.match.number,
                       },
                     }).test(body).should.equal(true);
                     return true;
                   })
             .reply(204);
           //acdLink debordement:
           updateCaller(`${urlFallback }?overflowcause=NO_AGENT`, callback);
         },
         function (callback) {
           matchEvt("evt_AcdTransferred_InProgress", {
             acdcause: "ACD_OVERFLOW",
             overflowcause: "NO_AGENT",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_InProgress_Terminated", {
             cause: "IVR_HANG_UP",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
         function (callback) {
           nock2.done();
           nock3.done();
           callback();
         },
       ], done);
       createInboundCall("script656_2");
     });

  it("T13: doit simuler un appel transfert sur file echec " +
     "car full_queue et debordement sur un raccroché", function (done) {
       var urlFallback, nock3;
       storeObjects.nockAllSounds();
       var nock2 = nock(`${acdLinkUrl }/call/`)
         .log(console.log)
         .post(/.*enqueue$/, function (body) {
           sinon.match({
             call_id: call.call_id,
             account_id: call.account_id,
             application_id: call.application_id,
             to: call.to,
             from: call.from,
             queue_id: "queue_id2",
             callTime: sinon.match.number,
             ivr_fallback: sinon.match(function (fallback) {
               urlFallback = fallback;
               return _.isString(fallback);
             }),
           }).test(body).should.equal(true);
           return true;
         })
         .reply(200, {
           waitSound: "waitsound",
         });
       storeObjects.nockScript("script656_2");
       async.series([
         function (callback) {
           matchEvt("evt_Created_AcdTransferred", {
             queueid: "queue_id2",
           }, callback);
         },
         function (callback) {
           nock3 = nock(acdLinkUrl)
             .log(console.log)
             .post(`/call/${ call.call_id }/principal/status`,
                   function (body) {
                     sinon.match({
                       call_id: call.call_id,
                       account_id: call.account_id,
                       status: "completed",
                       IVRStatus: {
                         state: "Terminated",
                         lastChange: sinon.match.number,
                       },
                     }).test(body).should.equal(true);
                     return true;
                   })
             .reply(204);
           //acdLink debordement:
           updateCaller(`${urlFallback }?overflowcause=FULL_QUEUE`, callback);
         },
         function (callback) {
           matchEvt("evt_AcdTransferred_Terminated", {
             acdcause: "ACD_OVERFLOW",
             overflowcause: "FULL_QUEUE",
             cause: "IVR_HANG_UP",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           _.delay(callback, 100);
         },
         function (callback) {
           nock2.done();
           nock3.done();
           callback();
         },
       ], done);
       createInboundCall("script656_2");
     });

  it("T14_IVR: doit envoyer les tickets IVR transfert sda reussi, " +
     "précedé d une annonce", function (done) {
       storeObjects.nockAllSounds();
       storeObjects.nockLimitManager();
       var lNock = storeObjects.nockScript("script666");
       async.series([
         function (callback) {
           tickets.once("change_Created_InProgress", callback);
         },
         function (callback) {
           matchEvt("evt_InProgress_TransferRinging", {
             ringingSda: "0620870375",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_Transferred", {
             sda: "0620870375",
           }, callback);
         },
         waitIncrDecrLimitter,
         function (callback) {
           storeObjects.getCallOnSda("0620870375").should.equal(1);
           callback();
           hangUpDialLegB();
         },
         function (callback) {
           //attendre la fin de l appel
           matchEvt("evt_Transferred_Terminated", {
             sda: "0620870375",
             bridgecause: "hangup-b",
             bridgeduration: sinon.match.string,
           }, callback);
         },
         waitIncrDecrLimitter,
         function(callback) {
           setTimeout(callback, 200);
         },
         function (callback) {
           storeObjects.getCallOnSda("0620870375").should.equal(0);
           lNock.done();
           callback();
         },
       ], done);
       createInboundCall("script666");
     });

  it("T14_ACTION: doit envoyer les tickets ACTION transfert sda reussi, " +
     "précedé d une annonce", function (done) {
       storeObjects.nockAllSounds();
       storeObjects.nockLimitManager();
       var lNock = storeObjects.nockScript("script666");
       var _callTime;
       var lastEvt;
       async.series([
         function (callback) {
           //attendre le debut d appel
           tickets.once("evt_Created_InProgress", function (evt) {
             _callTime = evt.callTime;
             callback();
           });
         },
         function (callback) {
           tickets.once("action_announcement_Mon annonce", function (evt) {
             sinon.match({
               producer: "IVR",
               subject: "ACTION",
               accountid: "accountid1",
               applicationid: "appID",
               from: "0123456789",
               to: "+338AB",
               scriptid: "script666",
               callTime: sinon.match.number,
               time: sinon.match.number,
               action: {type: "announcement", name: "Mon annonce"},
               endCause: undefined,
             }).test(evt).should.equal(true);
             lastEvt = evt;
             evt.callTime.should.be.equal(_callTime);
             checkDurationFirstAction(evt);
             callback();
           });
         },
         function (callback) {
           //attendre la transfert
           matchEvt("evt_TransferRinging_Transferred", {
             sda: "0620870375",
           }, callback);
         },
         function (callback) {
           setTimeout(callback, 200);
         },
         function (callback) {
           //le nombre d appels en cours sur le sda:
           _.defer(function () {
             storeObjects.getCallOnSda("0620870375").should.equal(1);
             callback();
           });

         },
         function (callback) {
           tickets.once("action_transfersda_Mon transfert SDA", function (evt) {
             sinon.match({
               producer: "IVR",
               subject: "ACTION",
               accountid: "accountid1",
               applicationid: "appID",
               from: "0123456789",
               to: "+338AB",
               scriptid: "script666",
               callTime: sinon.match.number,
               time: sinon.match.number,
               action: {type: "transfersda", name: "Mon transfert SDA"},
             }).test(evt).should.equal(true);
             evt.callTime.should.be.below(evt.time);
             evt.callTime.should.be.equal(lastEvt.callTime);
             evt.duration.should.be.equal(evt.time - lastEvt.time);
             callback();
           });
           //raccrocher le legB après avoir vérifié
           hangUpDialLegB();
         },
         function (callback) {
           //attendre la fin appel
           _.delay(callback, 200);
         },
         function (callback) {
           //nombre d appel en cours =0
           storeObjects.getCallOnSda("0620870375").should.equal(0);
           lNock.done();
           callback();
         },
       ], done);
       createInboundCall("script666");
     });


  it("T15: test business context après un Fetch", function (done) {
    storeObjects.nockAllSounds();
    storeObjects.nockAllScripts();
    async.series([
      function (callback) {
        tickets.once("change_Created_InProgress", callback);
      },
      function (callback) {
        //attendre le Fetch
        _.delay(callback, 100);
      },
      function (callback) {
        supertest(serverUrl)
          .get(`/account/${ call.account_id }/call/${ call.call_id}`)
          .expect(200)
          .end(function (err, res) {
            if (err) {
              return callback(err);
            }
            sinon.match({
              accountid: call.account_id,
              callid: call.call_id,
              CALLER: call.from,
              CALLEE: call.to,
              scriptid: "script739",
              callTime: sinon.match.number,
              VarContext2: "__FAILED__",
            }).test(res.body).should.equal(true, "le contexte de l'appel est faux");
            hangUpCaller();
            callback();
          });
      },
      function (callback) {
        //attendre tous les status CCAPI
        _.delay(callback, 100);
      },
    ], done);
    createInboundCall("script739");
  });

  it("T16: doit simuler un appel avec un transfert 1er item de la liste reussi " +
     "et le consultant raccroche", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockLimitManager();
       var ivrServicesNock = nock(ivrServicesUrl)
         .log(console.log)
         .post("/account/accountid1/destinationlist/listid1/eval", {})
         .once()
         .reply(200, {
           sda: "0620870375",
         });
       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "0620870375",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_Transferred", {
             sda: "0620870375",
           }, callback);
         },
         waitIncrDecrLimitter,
         function (callback) {
           storeObjects.getCallOnSda("0620870375").should.equal(1);
           callback();
           hangUpDialLegB();
         },
         function (callback) {
           matchEvt("evt_Transferred_Terminated", {
             sda: "0620870375",
             bridgecause: "hangup-b",
             bridgeduration: sinon.match.string, //??
           }, callback);
         },
         waitIncrDecrLimitter,
         function (callback) {
           storeObjects.getCallOnSda("0620870375").should.equal(0);
           //attendre tous les status CCAPI
           ivrServicesNock.done();
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script684");
     });


  function nockIvrServices() {
    return nock(ivrServicesUrl)
      .log(console.log)
      .post("/account/accountid1/destinationlist/listid1/eval", {})
      .once()
      .reply(200, {
        sda: "busySDA",
        param1: "value1",
        param2: "value2",
      })
      .post("/account/accountid1/destinationlist/listid1/eval", {
        param1: "value1",
        param2: "value2",
      })
      .once()
      .reply(200, {
        sda: "busySDA",
        param1: "busyV2",
        param2: "value2",
      })
      .post("/account/accountid1/destinationlist/listid1/eval", {
        param1: "busyV2",
        param2: "value2",
      })
      .reply(200, {
        sda: "no-answerSDA",
        paramBis1: "value1",
        param2: "value2",
      })
      .post("/account/accountid1/destinationlist/listid1/eval", {
        paramBis1: "value1",
        param2: "value2",
      })
      .once()
      .reply(404, {
        status: 404,
        apiCode: "sda_not_found",
      });
  }
  it("T17: doit simuler un appel transfert list, 2 items busy, 3ieme elt no answer, " +
     "erreur liste, puis dissuasion IVR", function (done) {
       storeObjects.nockAllScripts();
       var ivrServicesNock = nockIvrServices();

       async.parallel([statsAction, statsIvr], done);

       function statsAction(callback) {
         tickets.once("action_transferlist_mon transfert liste", function (evt) {
           sinon.match({
             producer: "IVR",
             subject: "ACTION",
             accountid: "accountid1",
             applicationid: "appID",
             from: "0123456789",
             to: "+338AB",
             scriptid: "script684",
             action: {type: "transferlist", name: "mon transfert liste"},
             endCause: "IVR_HANG_UP",
             callTime: sinon.match.number,
             time: sinon.match.number,
             state: undefined,
           }).test(evt).should.equal(true);
           checkDurationFirstAction(evt);
           callback();
         });
       }

       function statsIvr(callback) {
         async.series([
           function (cbk) {
             matchEvt("evt_Created_TransferRinging", {
               ringingSda: "busySDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_TransferRinging", {
               failedSda: "busySDA",
               dialcause: "busy",
               ringingSda: "busySDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_TransferRinging", {
               failedSda: "busySDA",
               dialcause: "busy",
               ringingSda: "no-answerSDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_Terminated", {
               failedSda: "no-answerSDA",
               dialcause: "no-answer",
               cause: "IVR_HANG_UP",
               ccapi_cause: "xml-hangup",
             }, cbk);
           },
           function (cbk) {
             //attendre tous les status CCAPI
             ivrServicesNock.done();
             _.delay(cbk, 100);
           },
         ], callback);
       }
       createInboundCall("script684");
     });

  it("T18: doit simuler un appel transfert list, 2elts busy, 3ieme elt no answer, " +
     "erreur liste puis debordement sur annonce et dissuasion IVR", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       var ivrServicesNock = nockIvrServices();

       async.series([
         function (callback) {
           matchEvt("evt_Created_TransferRinging", {
             ringingSda: "busySDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_TransferRinging", {
             failedSda: "busySDA",
             dialcause: "busy",
             ringingSda: "busySDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_TransferRinging", {
             failedSda: "busySDA",
             dialcause: "busy",
             ringingSda: "no-answerSDA",
           }, callback);
         },
         function (callback) {
           matchEvt("evt_TransferRinging_InProgress", {
             failedSda: "no-answerSDA",
             dialcause: "no-answer",
             cause: undefined,
             ccapi_cause: undefined,
           }, callback);
         },
         function (callback) {
           matchEvt("evt_InProgress_Terminated", {
             failedSda: sinon.match(_.isUndefined),
             dialcause: sinon.match(_.isUndefined),
             sda: sinon.match(_.isUndefined),
             cause: "IVR_HANG_UP",
             ccapi_cause: "xml-hangup",
           }, callback);
         },
         function (callback) {
           //attendre tous les status CCAPI
           ivrServicesNock.done();
           _.delay(callback, 100);
         },
       ], done);
       createInboundCall("script684_1");
     });


  it("T19: doit simuler un appel transfert list, 2 elts busy, 3ième elt no answer, erreur liste" +
     " puis debordement sur annonce et abadon pdt cette annonce+verif statsAction",
     function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       var ivrServicesNock = nockIvrServices();
       async.parallel([statsAction, statsIvr], done);

       function statsAction(callback) {
         var lastEvt;
         async.series([
           function (cbk) {
             tickets.once("action_transferlist_mon transfert liste", function (evt) {
               sinon.match({
                 producer: "IVR",
                 subject: "ACTION",
                 accountid: "accountid1",
                 applicationid: "appID",
                 from: "0123456789",
                 to: "+338AB",
                 scriptid: "script684_1",
                 action: {type: "transferlist", name: "mon transfert liste"},
                 endCause: undefined,
                 state: undefined,
                 callTime: sinon.match.number,
                 time: sinon.match.number,
               }).test(evt).should.equal(true);
               lastEvt = evt;
               checkDurationFirstAction(evt);
               cbk();
             });
           },
           function (cbk) {
             tickets.once("action_announcement_Mon annonce", function (evt) {
               sinon.match({
                 producer: "IVR",
                 subject: "ACTION",
                 accountid: "accountid1",
                 applicationid: "appID",
                 from: "0123456789",
                 to: "+338AB",
                 scriptid: "script684_1",
                 action: {type: "announcement", name: "Mon annonce"},
                 endCause: "CALLER_HANG_UP",
                 state: undefined,
                 callTime: sinon.match.number,
                 time: sinon.match.number,
               }).test(evt).should.equal(true);
               evt.duration.should.be.equal(evt.time - lastEvt.time);
               cbk();
             });
           }], callback);
       }

       function statsIvr(callback) {
         async.series([
           function (cbk) {
             matchEvt("evt_Created_TransferRinging", {
               ringingSda: "busySDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_TransferRinging", {
               failedSda: "busySDA",
               dialcause: "busy",
               ringingSda: "busySDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_TransferRinging", {
               failedSda: "busySDA",
               dialcause: "busy",
               ringingSda: "no-answerSDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_InProgress", {
               failedSda: "no-answerSDA",
               dialcause: "no-answer",
               cause: undefined,
               ccapi_cause: undefined,
             }, cbk);
           },
           function (cbk) {
             hangUpCaller();
             matchEvt("evt_InProgress_Terminated", {
               failedSda: undefined,
               dialcause: undefined,
               sda: undefined,
               cause: "CALLER_HANG_UP",
               ccapi_cause: undefined,
             }, cbk);
           },
           function (cbk) {
             //attendre tous les status CCAPI
             ivrServicesNock.done();
             _.delay(cbk, 100);
           },
         ], callback);
       }
       createInboundCall("script684_1");
     });

  it("T20: doit simuler un appel transfert list, 1er elt busy, 2ieme elt no answer abandon " +
     "pdt la sonnerie du 2iem elt, vérif stats action", function (done) {
       storeObjects.nockAllScripts();
       storeObjects.nockAllSounds();
       var ivrServicesNock = nock(ivrServicesUrl)
         .log(console.log)
         .post("/account/accountid1/destinationlist/listid1/eval", {})
         .once()
         .reply(200, {
           sda: "busySDA",
           param1: "value1",
           param2: "value2",
         })
         .post("/account/accountid1/destinationlist/listid1/eval", {
           param1: "value1",
           param2: "value2",
         })
         .once()
         .reply(200, {
           sda: "no-answerSDA",
           paramBis1: "value1",
           param2: "value2",
         });

       async.parallel([statsAction, statsIvr], done);

       function statsAction(callback) {
         async.series([
           function (cbk) {
             tickets.once("action_transferlist_mon transfert liste", function (evt) {
               sinon.match({
                 producer: "IVR",
                 subject: "ACTION",
                 accountid: "accountid1",
                 applicationid: "appID",
                 from: "0123456789",
                 to: "+338AB",
                 scriptid: "script684_1",
                 action: {type: "transferlist", name: "mon transfert liste"},
                 endCause: "CALLER_HANG_UP",
                 state: undefined,
                 callTime: sinon.match.number,
                 time: sinon.match.number,
               }).test(evt).should.equal(true);
               checkDurationFirstAction(evt);
               cbk();
             });
           }], callback);
       }

       function statsIvr(callback) {
         async.series([
           function (cbk) {
             matchEvt("evt_Created_TransferRinging", {
               ringingSda: "busySDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_TransferRinging", {
               failedSda: "busySDA",
               dialcause: "busy",
               ringingSda: "no-answerSDA",
             }, cbk);
           },
           function (cbk) {
             matchEvt("evt_TransferRinging_Terminated", {
               ringingSda: "no-answerSDA",
               cause: "CALLER_HANG_UP",
               sda: undefined,
               failedSda: undefined,
               dialcause: undefined,
             }, cbk);
             hangUpCaller();
           },
           function (cbk) {
             //attendre tous les status CCAPI
             ivrServicesNock.done();
             _.delay(cbk, 100);
           },
         ], callback);
       }

       createInboundCall("script684_1");
     });


});
