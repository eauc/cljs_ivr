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
const sinon = require("sinon");

describe("SMARTCC-1072 traitement du onend", function () {
  beforeEach(function (done) {
    var ctxt = this;
    ctxt.allNock = {};
    ctxt.allTickets = {
      ACTION: [],
      CALL: [],
    };
    ctxt.confServer = {
      confLogger: "DEFAULT_CONSOLE_DEBUG",
      environment: {
        dispatch_url: {
          internal: "http://dispatch_url",
        }
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
    async.series([
      function (cbk) {
        server.start(ctxt.confServer)
          .then(() => cbk(), cbk);
      },
      function (cbk) {
        // ctxt.options = cloudTools.configService.getConfig();
        ctxt.ivr_url = `http://localhost:${ ctxt.confServer.port }/smartccivr`;
        ctxt.drivenCall = new DrivenCall(ctxt.ivr_url);
        ivr.services.tickets.send_string = function (socket, data) {
          const evt = JSON.parse(data);
          ctxt.allTickets[evt.subject].push(evt);
          if (evt.state && evt.nextState) {
            tickets.emit(`change_${ evt.state }_${ evt.nextState}`);
          }
        };
        _.defer(cbk);
      },
    ], done);
  });

  describe("", function () {
    beforeEach(function () {
      var testScript = {
        _id: "scriptId",
        account_id: "accountId",
        nodes: {
          1: {
            cancelKey: "*",
            validateKey: "4",
            type: "voicerecord",
            varname: "var_name",
          },
        },
        start: 1,
      };
      this.allNock.cloudstore = nock(`${this.confServer.environment.dispatch_url.internal }/cloudstore`)
        .persist()
        .get("/account/accountId/script/scriptId")
        .reply(200, testScript);
    });

    it("T02: joue un enregistrement et raccroche pdt l enregistrement", function (done) {
      var ctxt = this;
      var onEndPromise = expectOnEndToBePlayed(this);
      playTestScript(this)
        .then(function () {
          return hangupAndWaitTerminated(ctxt);
        })
        .then(function () {
          return onEndPromise;
        })
        .then(function () {
          expectCallTicketsToBeEmitted(ctxt);
        })
        .then(function () {
          // leave time for memory cleanup
          return new Promise(function (res) {
            _.delay(res, 500);
          });
        })
        .then(done, done);
    });
  });

  afterEach(function () {
    _.each(this.allNock, function (nockInstance) {
      nockInstance.done();
    });
    nock.cleanAll();
  });

  // afterEach(function () {
  //   CallStatus.getCalls().should.be.empty; //pas de fuite de memoire...
  // });

  afterEach(function () {
    server.stop();
  });
});

function playTestScript(ctxt) {
  return ctxt.drivenCall
    .ringInProgressAndStartPromise("scriptId")
    .then(function (sce) {
      sce.should.deep.equals({
        Record: {
          "#name": "Record",
          callbackurl: "/smartccivr/script/scriptId/node/1/callback",
          finishonkey: "4*",
          playbeep: "playbeep",
          maxlength: "300",
        },
      });
    });
}

function hangupAndWaitTerminated(ctxt) {
  return Promise.all([
    // Hangup while script is running
    ctxt.drivenCall.terminateCallStatus("user-hangup"),
    promiseStateChange("InProgress", "Terminated"),
  ]);
}

function promiseStateChange(from, next) {
  return new Promise(function (resolve) {
    tickets.once(`change_${ from }_${ next}`, resolve);
  });
}

function expectOnEndToBePlayed(ctxt) {
  ctxt.allNock.ivr_service = nock(`${ctxt.confServer.environment.dispatch_url.internal}/smartccivrservices`);
  return new Promise(function (resolve) {
    ctxt.allNock.ivr_service
      .post("/account/accountId/script/scriptId/onend", function (body) {
        sinon.match({
          accountid: "accountId",
          callid: "callId",
          CALLER: "from",
          CALLEE: "to",
          scriptid: "scriptId",
        }).test(body).should.equal(true);
        resolve();
        return true;
      })
      .once()
      .reply(202);
  });
}

function expectCallTicketsToBeEmitted(ctxt) {
  _.map(ctxt.allTickets.CALL, function (ticket) {
    return _.pick(ticket, "state", "nextState");
  }).should.deep.equal([
    {state: "Created", nextState: "InProgress"},
    {state: "InProgress", nextState: "Terminated"},
  ]);
  ctxt.allTickets.ACTION.should.deep.equal([]);
}
