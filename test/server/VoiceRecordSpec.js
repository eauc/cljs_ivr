/* global Promise */

"use strict";

var server = require("./helpers/server");
var _ = require("underscore");
var TicketsEmitter = require("events");
var async = require("async");
var nock = require("nock");
require("chai").should();
var storeObjects = require("./helpers/Store");
var supertestPromised = require("supertest-as-promised");
var xml2js = require("xml2js");
var URL = require("url");
var tickets = new TicketsEmitter();
const sinon = require("sinon");

var parseString = new xml2js.Parser({
  trim: true,
  explicitRoot: false,
  explicitArray: false,
  mergeAttrs: true,
  explicitChildren: true,
  preserveChildrenOrder: true,
  charkey: "content",

}).parseString;

var parseXMlRes = function (httpRes) {
  return new Promise(function (resolve, reject) {
    parseString(httpRes.text, function (err, res) {
      return err ? reject(err) : resolve(_.omit(res, "#name"));
    });
  });
};

describe("SMARTCC-637 Implémentation d'un script avec voicerecord", function () {

  this.timeout(5000);
  var options, ivrUrl, call;
  var confServer = {
    confLogger: "DEFAULT_CONSOLE_DEBUG",
    environment: {
      dispatch_url: {
        internal: "http://ic.test.smartcc:8080",
      }
    },
    port: 8061,
    business: {
      voicerecord: {
        maxlength: 180,
      },
    },
    zmq: {
      activeZMQ: true,
      publishTo: "tcp://*:5571",
      publisherName: "IVR",
      ticket_version: 1.1,
    },
  };

  var deferPromise = function () {
    return new Promise(function (res) {
      _.defer(res);
    });
  };

  beforeEach(function (done) {
    call = {
      account_id: "accountid1",
      application_id: "appID",
      call_id: "callID",
      status: "ringing",
      from: "from",
      to: "to",
    };
    async.series([
      function (cb) {
        server.start(confServer)
          .then(() => cb(), cb);
      },
      function (callback) {
        _.defer(callback);
      },
    ], done);
  });
  beforeEach(function () {
    tickets = new TicketsEmitter();
      ivrUrl = `http://localhost:${ confServer.port }/smartccivr`;
    ivr.services.tickets.send_string = function (socket, data) {
      const evt = JSON.parse(data);
      if (evt.state && evt.nextState) {
        tickets.emit(`change_${ evt.state }_${ evt.nextState}`);
        tickets.emit(`evt_${ evt.state }_${ evt.nextState}`, evt);
      } else if (evt.action) {
        tickets.emit(`action_${ evt.action.type }_${ evt.action.name}`, evt);
      }
    };
  });

  afterEach(function () {
    server.stop();
  });

  afterEach(function () {
    nock.cleanAll();
  });

  it("T01: callcontrol, record réussi suivi d une annonce", function () {
    storeObjects.nockAllScripts();
    storeObjects.nockAllSounds();
    var relativeUrl;
    return supertestPromised(ivrUrl)
      .post("/script/script637/status")
      .type("form")
      .send(call) //sonnerie d un appel
      .expect(204)
      .then(() => {
        return supertestPromised(ivrUrl)
          .post("/script/script637/node/start")
          .type("form")
          .send(call) //arrivé d un appel:
          .expect(200);
      })
      .then(parseXMlRes)
      .then(function (res) {
        relativeUrl = URL.resolve("/script/script637/node/start",
                                  res.Record.callbackurl.replace(/^\/smartccivr/, ""));
        res.should.deep.equal({Record: {
          playbeep: "playbeep",
          maxlength: String(confServer.business.voicerecord.maxlength),
          finishonkey: "4*",
          callbackurl: "/smartccivr/script/script637/node/1/callback",
          "#name": "Record"},
                              });

        call.status = "in-progress";//envoie du status
        return supertestPromised(ivrUrl)
          .post("/script/script637/status")
          .type("form")
          .send(call)
          .expect(204);
      })
      .then(function () {
        //l enregistrement finit par la touche 4: //validationKey
        var callbackAttrs = _.extend({
          record_cause: "digit-a",
          record_url: "ccapi://Record/record_id",
          record_digits: "24",
        }, call);
        return supertestPromised(ivrUrl)
          .post(relativeUrl)
          .type("form")
          .send(callbackAttrs)
          .expect(200)
          .then(parseXMlRes);
      })
      .then(function (sce) {
        relativeUrl = URL.resolve(
          relativeUrl,
          sce.Redirect.content.replace(/^\/smartccivr/, "")
        );
        sce.should.deep.equal({Redirect: {
          content: "/smartccivr/script/script637/node/3",
          "#name": "Redirect",
        }});
        //récuperation du context:
        return supertestPromised(ivrUrl)
          .get("/account/accountid1/call/callID")
          .expect(200);
      })
      .then(function (businessCtx) {
        businessCtx.body.should.not.have.property("voicerecorde_1_smtp_ok");
        businessCtx.body.should.have.property("var_name_4_to_5",
                                              "ccapi://Record/record_id");
        //recuperation scenarion node3:
        return supertestPromised(ivrUrl)
          .post(relativeUrl)
          .type("form")
          .send(call)
          .expect(200)
          .then(parseXMlRes);
      })
      .then(function (sce) {
        sce.should.deep.equal({
          Gather: {
            Play: {
              content: "/cloudstore/file/autreSoundId",
              "#name": "Play",
            },
            "#name": "Gather",
            callbackmethod: "POST",
            callbackurl: "/smartccivr/script/script637/node/3/callback",
            numdigits: "1",
            timeout: "1",
          },
          HangUp: {
            "#name": "HangUp",
          },
        });
        //récuperation du context:
        return supertestPromised(ivrUrl)
          .get("/account/accountid1/call/callID")
          .expect(200);
      })
      .then(function (businessCtx) {
        businessCtx.body.should.not.have.property("voicerecorde_1_smtp_ok");

        const ticketReceivedPromise = new Promise((resolve) => {
          tickets.once("evt_InProgress_Terminated", function (evt) {
            sinon.match({
              nextState: "Terminated",
              cause: "IVR_HANG_UP"
            }).test(evt).should.equal(true);
            resolve();
          });
        });

        //fin de l appel :
        call.status = "completed";
        call.cause = "xml-end";
        return supertestPromised(ivrUrl)
          .post("/script/script637/status")
          .type("form")
          .send(call)
          .expect(204)
          .then(() => ticketReceivedPromise);
      });
  });

  it("T02: callcontrol, record echec suivi d une annonce avec abandon durant l annonce", function (done) {
    storeObjects.nockAllScripts();
    storeObjects.nockAllSounds();
    var relativeUrl;
    supertestPromised(ivrUrl)
      .post("/script/script637/status")
      .type("form")
      .send(call) //sonnerie d un appel
      .expect(204)
      .then(() => {
        return supertestPromised(ivrUrl)
          .post("/script/script637/node/start")
          .type("form")
          .send(call) //arrivé d un appel:
          .expect(200);
      })
      .then(parseXMlRes)
      .then(function (res) {
        relativeUrl = URL.resolve("/script/script637/node/start",
                                  res.Record.callbackurl.replace(/^\/smartccivr/,""));
        res.should.deep.equal({Record: {
          playbeep: "playbeep",
          maxlength: String(confServer.business.voicerecord.maxlength),
          finishonkey: "4*",
          callbackurl: "/smartccivr/script/script637/node/1/callback",
          "#name": "Record"},
                              });

        call.status = "in-progress";//envoie du status
        return supertestPromised(ivrUrl)
          .post("/script/script637/status")
          .type("form")
          .send(call)
          .expect(204);
      })
      .then(function () {
        //l enregistrement finit par la touche *: //cancelKey
        var callbackAttrs = _.extend({
          record_cause: "digit-a",
          record_url: "ccapi://Record/record_id",
          record_digits: "*",
        }, call);
        return supertestPromised(ivrUrl)
          .post(relativeUrl)
          .type("form")
          .send(callbackAttrs)
          .expect(200)
          .then(parseXMlRes);
      })
      .then(function (sce) {
        relativeUrl = URL.resolve(relativeUrl,
                                  sce.Redirect.content.replace(/^\/smartccivr/,""));
        sce.should.deep.equal({Redirect: {
          content: "/smartccivr/script/script637/node/2",
          "#name": "Redirect",
        }});
        //récuperation du context:
        return supertestPromised(ivrUrl)
          .get("/account/accountid1/call/callID")
          .expect(200);
      })
      .then(function (businessCtx) {
        businessCtx.body.should.not.have.property("var_name_4_to_5");
        //recuperation scenarion node du redirect:
        return supertestPromised(ivrUrl)
          .post(relativeUrl)
          .type("form")
          .send(call)
          .expect(200)
          .then(parseXMlRes);
      })
      .then(function (sce) {
        sce.should.deep.equal({
          Gather: {
            Play: {
              content: "/cloudstore/file/autreSoundId",
              "#name": "Play",
            },
            "#name": "Gather",
            callbackmethod: "POST",
            callbackurl: "/smartccivr/script/script637/node/2/callback",
            numdigits: "1",
            timeout: "1",
          },
          HangUp: {
            "#name": "HangUp",
          },
        });
        //fin de l appel :
        call.status = "completed";
        call.cause = "user-hangup";
        Promise.all([new Promise(function (resolve) {
          tickets.once("evt_InProgress_Terminated", function (evt) {
            sinon.match({
              nextState: "Terminated",
              cause: "CALLER_HANG_UP"
            }).test(evt).should.equal(true);
            resolve();
          });
        }), supertestPromised(ivrUrl)
                     .post("/script/script637/status")
                     .type("form")
                     .send(call)
                     .expect(204)]);
      })
      .then(deferPromise)
      .then(done, done);
  });

  it("T03: callcontrol, record echec sans next", function (done) {
    storeObjects.nockAllScripts();
    var relativeUrl;
    supertestPromised(ivrUrl)
      .post("/script/script637_2/status")
      .type("form")
      .send(call) //sonnerie d un appel
      .expect(204)
      .then(() => {
        return supertestPromised(ivrUrl)
          .post("/script/script637_2/node/start")
          .type("form")
          .send(call) //arrivé d un appel:
          .expect(200);
      })
      .then(parseXMlRes)
      .then(function (res) {
        relativeUrl = URL.resolve("/script/script637_2/node/start",
                                  res.Record.callbackurl.replace(/^\/smartccivr/,""));
        res.should.deep.equal({Record: {
          playbeep: "playbeep",
          maxlength: String(confServer.business.voicerecord.maxlength),
          finishonkey: "4#",
          callbackurl: "/smartccivr/script/script637_2/node/1/callback",
          "#name": "Record"},
                              });

        call.status = "in-progress";//envoie du status
        return supertestPromised(ivrUrl)
          .post("/script/script637_2/status")
          .type("form")
          .send(call)
          .expect(204);
      })
      .then(function () {
        //l enregistrement finit par la touche cancelKey
        var callbackAttrs = _.extend({
          record_cause: "digit-a",
          record_url: "ccapi://Record/record_id",
          record_digits: "#",
        }, call);
        return supertestPromised(ivrUrl)
          .post(relativeUrl)
          .type("form")
          .send(callbackAttrs)
          .expect(200)
          .then(parseXMlRes);
      })
      .then(function (sce) {
        sce.should.deep.equal({HangUp: {
          "#name": "HangUp",
        }});
        //récuperation du context:
        return supertestPromised(ivrUrl)
          .get("/account/accountid1/call/callID")
          .expect(200);
      })
      .then(function (businessCtx) {
        businessCtx.body.should.not.have.property("var_name_4_to_5");
        //racroché IVR fin de l appel :
        call.status = "completed";
        call.cause = "hangup-xml";

        tickets.once("evt_InProgress_Terminated", function (evt) {
          sinon.match({
            nextState: "Terminated",
            cause: "IVR_HANG_UP"
          }).test(evt).should.equal(true);
          done();
        });
        return supertestPromised(ivrUrl)
          .post("/script/script637_2/status")
          .type("form")
          .send(call)
          .expect(204);
      })
      .catch(done);
  });

  it("T04: callcontrol, record success sans next", function (done) {
    storeObjects.nockAllScripts();
    var relativeUrl;
    supertestPromised(ivrUrl)
      .post("/script/script637_2/status")
      .type("form")
      .send(call) //sonnerie d un appel
      .expect(204)
      .then(() => {
        return supertestPromised(ivrUrl)
          .post("/script/script637_2/node/start")
          .type("form")
          .send(call) //arrivé d un appel:
          .expect(200);
      })
      .then(parseXMlRes)
      .then(function (res) {
        relativeUrl = URL.resolve("/script/script637_2/node/start",
                                  res.Record.callbackurl.replace(/^\/smartccivr/, ""));
        res.should.deep.equal({Record: {
          playbeep: "playbeep",
          maxlength: String(confServer.business.voicerecord.maxlength),
          finishonkey: "4#",
          callbackurl: "/smartccivr/script/script637_2/node/1/callback",
          "#name": "Record"},
                              });

        call.status = "in-progress";//envoie du status
        return supertestPromised(ivrUrl)
          .post("/script/script637_2/status")
          .type("form")
          .send(call)
          .expect(204);
      })
      .then(function () {
        //l enregistrement finit par la touche cancelKey
        var callbackAttrs = _.extend({
          record_cause: "digit-a",
          record_url: "ccapi://Record/record_id",
          record_digits: "4",
        }, call);
        return supertestPromised(ivrUrl)
          .post(relativeUrl)
          .type("form")
          .send(callbackAttrs)
          .expect(200)
          .then(parseXMlRes);
      })
      .then(function (sce) {
        sce.should.deep.equal({HangUp: {
          "#name": "HangUp",
        }});
        //récuperation du context:
        return supertestPromised(ivrUrl)
          .get("/account/accountid1/call/callID")
          .expect(200);
      })
      .then(function (businessCtx) {
        businessCtx.body.should.have.property("var_name_4_to_5",
                                              "ccapi://Record/record_id");
        //racroché IVR fin de l appel :
        call.status = "completed";
        call.cause = "hangup-xml";

        tickets.once("evt_InProgress_Terminated", function (evt) {
          sinon.match({
            nextState: "Terminated",
            cause: "IVR_HANG_UP"
          }).test(evt).should.equal(true);
          done();
        });
        return supertestPromised(ivrUrl)
          .post("/script/script637_2/status")
          .type("form")
          .send(call)
          .expect(204);
      })
      .catch(done);
  });
});
