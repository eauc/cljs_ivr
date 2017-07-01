/* global require, Promise*/
"use strict";

const server = require("./helpers/server.js");
var _ = require("underscore");
var TicketsEmitter = require("events");
var async = require("async");
var nock = require("nock");
require("chai").should();
var tickets = new TicketsEmitter();
var DrivenCall = require("./helpers/DrivenCallMock.js");

describe("SMARTCC-862 Implémentation des annonces interruptibles Tests de regressions", function () {

  this.timeout(5000);
  var ivr_url, drivenCall;
  var confServer = {
    environment: {
      dispatch_url: {
        internal: "http://dispatch_url",
      }
    },
    port: 8061,
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
      // function (cbk) {
      //   server = new Server(confServer, cbk);
      // },
      function (cb) {
        server
          .start(confServer)
          .then(() => cb(), cb);
      },
      function (cb) {
        ivr_url = `http://localhost:${confServer.port}/smartccivr`;
        drivenCall = new DrivenCall(ivr_url);
        ivr.services.tickets.send_string = function (socket, evt) {
          evt = JSON.parse(evt);
          console.log("receive", evt);
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
    // CallStatus.getCalls().should.be.empty; //pas de fuite de memoire...
    return server.stop();
  });

  it("T01: annonce non interruptible, suivi de deux annonces interruptibles", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
      .log(console.log)
      .get("/account/accountId/script/scriptId")
      .times(4)
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "announcement",
            no_barge: true,
            label: "Mon annonce",
            stat: {type: "announcement", name: "Mon annonce1"},
            soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
            next: 2,
            preset: {varname: "announce_1_smtp_ko", value: "1"},
          },
          2: {
            type: "announcement",
            label: "Mon annonceV2",
            soundname: "Meilleur_sonnerie_pour_portable_2016.ogg",
            next: 3,
          },
          3: {
            type: "announcement",
            no_barge: false,
            label: "Mon annonceV3",
            stat: {type: "announcement", name: "Mon annonce2"},
            soundname: "Meilleur_sonnerie_pour_portable_2016.ogg",
            preset: {varname: "announce_3_smtp_ko", value: "11111"},
          },
        },
        start: 1,
      })
      .get("/account/accountId/file")
      .once()
      .query({
        query: {
          filename: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "otisIdFile"}]})
      .get("/account/accountId/file")
      .times(2)
      .query({
        query: {
          filename: "Meilleur_sonnerie_pour_portable_2016.ogg",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "sonnerieIdFile"}]});


    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (sce) {
        sce.should.deep.equals({
          Play: {
            content: "/cloudstore/file/otisIdFile",
            "#name": "Play",
          },
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/2",
          },
        });
        return drivenCall.getbusinessCtx();
      })
      .then(function (businessCtx) {
        businessCtx.body.should.have.property("announce_1_smtp_ko", "1");
        return drivenCall.playNodePromise(2);
      })
      .then(function (res) {
        res.should.deep.equal({
          Gather: {
            Play: {
              content: "/cloudstore/file/sonnerieIdFile",
              "#name": "Play",
            },
            "#name": "Gather",
            callbackmethod: "POST",
            callbackurl: "/smartccivr/script/scriptId/node/2/callback",
            numdigits: "1",
            timeout: "1",
          },
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/3",
          },
        });

        //l utilisateur interrompt l annonce:
        return drivenCall.callbackNode(2, {termdigit: "2", digits: "42"});
      })
      .then(function (sce) {
        //redirect to the next:
        sce.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/3",
          },
        });
        return drivenCall.playNodePromise(3);
      })
      .then(function (sce) {
        //redirect to the next:
        sce.should.deep.equal({
          Gather: {
            Play: {
              content: "/cloudstore/file/sonnerieIdFile",
              "#name": "Play",
            },
            "#name": "Gather",
            callbackmethod: "POST",
            callbackurl: "/smartccivr/script/scriptId/node/3/callback",
            numdigits: "1",
            timeout: "1",
          },
          HangUp: {
            "#name": "HangUp",
          },
        });

        return drivenCall.getbusinessCtx();
      })
      .then(function (businessCtx) {
        businessCtx.body.should.have.property("announce_3_smtp_ko", "11111");
        //pas d interruption:
        return Promise.all([
          promiseStateChange("InProgress", "Terminated"),
          drivenCall.terminateCallStatus("xml-hangup"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (no) {
          no.done();
        });
        //verif tickets:
        //                                      console.log('allTickets.CALL:', allTickets.CALL);
        allTickets.CALL.length.should.equal(2);
        _.pick(allTickets.CALL[0], ["state", "nextState"]).should.deep.equal({
          state: "Created",
          nextState: "InProgress",
        });
        _.pick(allTickets.CALL[1], ["state", "nextState", "cause", "ccapi_cause"]).should.deep.equal({
          state: "InProgress",
          nextState: "Terminated",
          cause: "IVR_HANG_UP",
          ccapi_cause: "xml-hangup",
        });

        //                                      console.log('allTickets.ACTION:', allTickets.ACTION);
        allTickets.ACTION.length.should.equal(2);

        _.pick(allTickets.ACTION[0], ["action"]).should.deep.equal({
          action: {type: "announcement", name: "Mon annonce1"},
        });
        _.pick(allTickets.ACTION[1], ["action", "endCause"]).should.deep.equal({
          action: {type: "announcement", name: "Mon annonce2"},
          endCause: "IVR_HANG_UP",
        });
      })
      .then(done, done);
  });


  it("T02: derniere annonce interrompue", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(`${confServer.environment.dispatch_url.internal }/cloudstore`)
      .log(console.log)
      .get("/account/accountId/script/scriptId")
      .times(2)
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "announcement",
            label: "Mon annonce",
            stat: {type: "announcement", name: "Mon annonce1"},
            soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
          },
        },
        start: 1,
      })
      .get("/account/accountId/file")
      .once()
      .query({
        query: {
          filename: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
          "metadata.type": "scriptSound",
          "metadata.script": "scriptId",
        },
      })
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "otisIdFile"}]});


    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (res) {
        res.should.deep.equal({
          Gather: {
            "#name": "Gather",
            callbackmethod: "POST",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback",
            numdigits: "1",
            timeout: "1",
            Play: {
              content: "/cloudstore/file/otisIdFile",
              "#name": "Play",
            },
          },
          HangUp: {
            "#name": "HangUp",
          },
        });
        //l utilisateur interrompt l annonce:
        return drivenCall.callbackNode(1, {termdigit: "2", digits: "42"});
      })
      .then(function (sce) {
        //redirect to the next:
        sce.should.deep.equal({
          HangUp: {"#name": "HangUp"},
        });
        //dissuasion:
        return Promise.all([
          promiseStateChange("InProgress", "Terminated"),
          drivenCall.terminateCallStatus("xml-hangup"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (no) {
          no.done();
        });
        //verif tickets:
        //                                      console.log("allTickets.CALL:", allTickets.CALL);
        allTickets.CALL.length.should.equal(2);
        _.pick(allTickets.CALL[0], ["state", "nextState"]).should.deep.equal({
          state: "Created",
          nextState: "InProgress",
        });
        _.pick(allTickets.CALL[1], ["state", "nextState", "cause", "ccapi_cause"]).should.deep.equal({
          state: "InProgress",
          nextState: "Terminated",
          cause: "IVR_HANG_UP",
          ccapi_cause: "xml-hangup",
        });

        //                                      console.log("allTickets.ACTION:", allTickets.ACTION);
        allTickets.ACTION.length.should.equal(1);

        _.pick(allTickets.ACTION[0], ["action", "endCause"]).should.deep.equal({
          action: {type: "announcement", name: "Mon annonce1"},
          endCause: "IVR_HANG_UP",
        });
      })
      .then(done, done);
  });

});
