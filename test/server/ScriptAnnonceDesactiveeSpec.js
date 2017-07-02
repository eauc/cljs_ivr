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


describe("SMARTCC-1113 script avec une seule annonce desactivée", function () {

  this.timeout(5000);
  var options, ivr_url, drivenCall;
  var confServer = {
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
    //          console.log("=============++>");
    // CallStatus.getCalls().should.be.empty; //pas de fuite de memoire...
    server.stop();
  });

  it("T01: annonce desactivée, appel sortant", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
    //                          .log(console.log)
      .get("/account/accountId/script/scriptId")
      .once()
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "announcement",
            disabled: true,
            label: "Mon annonce",
            stat: {type: "announcement", name: "Mon annonce1"},
            soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
          },
        },
        start: 1,
      });

    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (sce) {
        sce.should.deep.equals({
          HangUp: {
            "#name": "HangUp",
          },
        });
        return Promise.all([
          drivenCall.terminateCallStatus("xml-hangup"),
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


  it("annonce descativé, appel entrant BUG 1179", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
    //                          .log(console.log)
      .get("/account/accountId/script/scriptId")
      .once()
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "announcement",
            disabled: true,
            label: "Mon annonce",
            stat: {type: "announcement", name: "Mon annonce1"},
            soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
          },
        },
        start: 1,
      });

    drivenCall
      .ringPromise("scriptId")
      .then(function () {
        return drivenCall.startPromise();
      })
      .then(function (sce) {
        sce.should.deep.equals({
          HangUp: {
            "#name": "HangUp",
          },
        });
        return Promise.all([
          drivenCall.cancelCallStatus("xml-hangup"),
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
