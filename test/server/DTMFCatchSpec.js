/* global require, Promise*/
"use strict";

var server = require("./helpers/server");
var _ = require("underscore");
var TicketsEmitter = require("events");
var async = require("async");
var nock = require("nock");
require("chai").should();
var tickets = new TicketsEmitter();
var DrivenCall = require("./helpers/DrivenCallMock");

describe("DTMFCatchTR contre les regressions de US1092 et US1192 : TTS online et Son en cas d echec", function () {

  var ivr_url, drivenCall;
  var confServer = {
    confLogger: "DEFAULT_CONSOLE_DEBUG",
    environment: {
      dispatch_url: {
        internal: "http://dispatch_url",
      },
    },
    port: 8061,
    business: {
      transfersda: {
        ringing_tone: "54cf6243d658e82f077e2327",
        ringingTimeoutSec: 10,
      },
    },
    zmq: {
      activeZMQ: true,
      publishTo: "tcp://*:5571",
      publisherName: "IVR",
      ticket_version: 1.1,
    },
  };
  var allTickets = {
    ACTION: [],
    CALL: [],
  };

  beforeEach(function (done) {
    allTickets = {
      ACTION: [],
      CALL: [],
    };
    async.series([
      function (cb) {
        server.start(confServer)
          .then(() => cb(), cb);
      },
      function (cb) {
        ivr_url = `http://localhost:${ confServer.port }/smartccivr`;
        drivenCall = new DrivenCall(ivr_url);
        ivr.services.tickets.send_string = function (socket, data) {
          const evt = JSON.parse(data);
          //                                                  console.log("receive", evt);
          allTickets[evt.subject].push(evt);
          if (evt.state && evt.nextState) {
            tickets.emit(`change_${ evt.state }_${ evt.nextState}`);
            tickets.emit(`evt_${ evt.state }_${ evt.nextState}`, evt);
          } else if (evt.action) {
            tickets.emit(`action_${ evt.action.type }_${ evt.action.name}`, evt);
          }
        };
        _.defer(cb);
      },
    ], done);
  });


  var promiseStateChange = function (from, next) {
    return new Promise(function (resolve) {
      tickets.once(`change_${ from }_${ next}`, resolve);
    });
  };
  afterEach(function () {
    nock.cleanAll();
  });

  afterEach(function () {
    server.stop();
  });

  it("T01: dtmf test qu on joue le son de retry", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "dtmfcatch",
            label: "mon dtmfcatch",
            welcome: [
              {soundname: "bienvenue1"},
              {soundname: "bienvenue2"},
            ],
            retry: {soundname: "raté"},
            numdigits: 4,
            finishonkey: "*",
            timeout: 5,
            max_attempts: 4,
            varname: "variable",
          },
        },
        start: 1,
      })
      .get("/account/accountId/file")
      .query({
        query: {
          filename: "bienvenue1",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "bienvenue1ID"}]})
      .get("/account/accountId/file")
      .query({
        query: {
          filename: "bienvenue2",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "bienvenue2ID"}]})
      .get("/account/accountId/file")
      .query({
        query: {
          filename: "raté",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "rateID"}]});

    drivenCall.orderedParse = true;
    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (sce) {
        sce.should.deep.equals([{
          "#name": "Gather",
          attrs: {
            finishonkey: "*",
            numdigits: "4",
            timeout: "5",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback?retries=1",
            callbackmethod: "POST",
          },
          childreen: [
            {"#name": "Play", content: "/cloudstore/file/bienvenue1ID"},
            {"#name": "Play", content: "/cloudstore/file/bienvenue2ID"},
          ],
        }, {
          "#name": "Redirect",
          content: "/smartccivr/script/scriptId/node/1/callback?retries=1",
        }]);
        return drivenCall.callbackNode(1, {
          retries: 1,
          digits: "21",
        });
      })
      .then((sce) => {
        sce.should.deep.equals([{
          "#name": "Gather",
          attrs: {
            finishonkey: "*",
            numdigits: "4",
            timeout: "5",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback?retries=2",
            callbackmethod: "POST",
          },
          childreen: [
            {"#name": "Play", content: "/cloudstore/file/rateID"},
            {"#name": "Play", content: "/cloudstore/file/bienvenue1ID"},
            {"#name": "Play", content: "/cloudstore/file/bienvenue2ID"},
          ],
        }, {
          content: "/smartccivr/script/scriptId/node/1/callback?retries=2",
          "#name": "Redirect",
        },
                               ]);
        return Promise.all([
          drivenCall.terminateCallStatus("user-hangup"),
          promiseStateChange("InProgress", "Terminated"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (no) {
          no.done();
        });
      })
      .then(done, done);
  });

  it("T02: dtmf test qu on joue des sons avec du TTS online", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "dtmfcatch",
            label: "mon dtmfcatch",
            stat: {type: "sType", name: "MonDTMF"},
            welcome: [
              {varname: "CALLER", voice: "claire", pronounce: "phone"},
              {soundname: "bienvenue2"},
              {varname: "_not_exists", voice: "claire", pronounce: "phone"},
              {varname: "CALLEE", voice: "claire", pronounce: "phone"},
              {varname: "scriptid", voice: "asse"},
            ],

            numdigits: 4,
            finishonkey: "*",
            timeout: 5,
            max_attempts: 4,
            varname: "variable",
          },
        },
        start: 1,
      })
      .get("/account/accountId/file")
      .query({
        query: {
          filename: "bienvenue2",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "bienvenue2ID"}]});

    drivenCall.call.from = "+33512345678";
    drivenCall.call.to = "0412345678";
    drivenCall.orderedParse = true;
    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (sce) {
        var spkV = (txt) => ({
          "#name": "Speak", content: `${txt }.`, attrs: {locutor: "claire"},
        });
        sce.should.deep.equals([{
          "#name": "Gather",
          attrs: {
            finishonkey: "*",
            numdigits: "4",
            timeout: "5",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback?retries=1",
            callbackmethod: "POST",
          },
          childreen: [
            spkV("05"), spkV("12"), spkV("34"), spkV("56"), spkV("78"),
            {"#name": "Play", content: "/cloudstore/file/bienvenue2ID"},
            spkV("04"), spkV("12"), spkV("34"), spkV("56"), spkV("78"),
            {"#name": "Speak", content: "scriptId", attrs: {locutor: "asse"}},
          ],
        }, {
          "#name": "Redirect",
          content: "/smartccivr/script/scriptId/node/1/callback?retries=1",
        }]);
        return Promise.all([
          drivenCall.terminateCallStatus("user-hangup"),
          promiseStateChange("InProgress", "Terminated"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (no) {
          no.done();
        });
      })
      .then(done, done);
  });


  it("T03: dtmf test qu on joue des sons avec du TTS online pour un numéro non français", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "dtmfcatch",
            label: "mon dtmfcatch",
            stat: {type: "sType", name: "MonDTMF"},
            welcome: [
              {varname: "CALLER", voice: "claire", pronounce: "phone"},
            ],
            numdigits: 4,
            finishonkey: "*",
            timeout: 5,
            max_attempts: 4,
            varname: "variable",
          },
        },
        start: 1,
      });
    drivenCall.call.from = "+3221238";
    drivenCall.orderedParse = true;
    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (sce) {
        var spkV = (txt) => ({
          "#name": "Speak", content: `${txt }.`, attrs: {locutor: "claire"},
        });
        sce.should.deep.equals([{
          "#name": "Gather",
          attrs: {
            finishonkey: "*",
            numdigits: "4",
            timeout: "5",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback?retries=1",
            callbackmethod: "POST",
          },
          childreen: [
            spkV("+"), spkV("3"), spkV("2"), spkV("2"),
            spkV("1"), spkV("2"), spkV("3"), spkV("8"),
          ],
        }, {
          "#name": "Redirect",
          content: "/smartccivr/script/scriptId/node/1/callback?retries=1",
        }]);
        return Promise.all([
          drivenCall.terminateCallStatus("user-hangup"),
          promiseStateChange("InProgress", "Terminated"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (no) {
          no.done();
        });
      })
      .then(done, done);
  });

});
