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

describe("SMARTCC-637 Implémentation des debordements Queue <-> TransfertSDA", function () {

  this.timeout(5000);
  var options, ivrUrl, drivenCall, lastEvt;
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
  var cloudStoreUrl = `${confServer.environment.dispatch_url.internal }/cloudstore`;
  var acdLinkUrl = `${confServer.environment.dispatch_url.internal }/smartccacdlink`;
  var ivrServicesUrl = `${confServer.environment.dispatch_url.internal }/smartccivrservices`;
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
      function (cbk) {
        server.start(confServer)
          .then(() => cbk(), cbk);
      },
      function (cbk) {
        ivrUrl = `http://localhost:${ confServer.port }/smartccivr`;
        drivenCall = new DrivenCall(ivrUrl);
        ivr.services.tickets.send_string = function (socket, data) {
          const evt = JSON.parse(data);
          if (evt.subject === "CALL") {
            lastEvt = evt;
          }
          allTickets[evt.subject].push(evt);
          if (evt.state && evt.nextState) {
            tickets.emit(`change_${ evt.state }_${ evt.nextState}`);
            tickets.emit(`evt_${ evt.state }_${ evt.nextState}`, evt);
          } else if (evt.action) {
            tickets.emit(`action_${ evt.action.type }_${ evt.action.name}`, evt);
          }
        };
        _.defer(cbk);
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

  afterEach(function (// done
                     ) {
    // CallStatus.getCalls().should.be.empty; //pas de fuite de memoire...
    server.stop();
  });

  var deferPromise = function () {
    return new Promise(function (res) {
      _.defer(res);
    });
  };
  var timestampChecker = function (candidate) {
    var dat = new Date(Number(candidate));
    return dat.getTime() === dat.getTime();
  };
  it("T01: test regression debordement transfert queue -> transfert sda", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "transferqueue",
            queue: "queue_id1",
            case: {
              noagent: 2,
            },
            stat: {type: "transferqueue", name: "mon_Transfert_Queue"},
          },
          2: {
            type: "transfersda",
            label: "Mon transfert SDA",
            dest: "0620870375",
            stat: {type: "transfersda", name: "Mon_transfert_SDA"},
          },
        },
        start: 1,
      });
    allNock.acdlink = nock(acdLinkUrl)
      .post("/call/callId/enqueue", function (body) {
        //                                      console.log('acdLink body:', body);
        sinon.match({
          call_id: drivenCall.call.call_id,
          account_id: drivenCall.call.account_id,
          application_id: drivenCall.call.application_id,
          from: drivenCall.call.from,
          to: drivenCall.call.to,
          callTime: sinon.match.number,
          queue_id: "queue_id1",
          ivr_fallback: "/smartccivr/script/scriptId/node/1/callback",
        }).test(body).should.equal(true);
        return true;
      })
      .reply(200, {
        waitSound: "waitsound",
      })
      .post("/call/callId/principal/status", function (body) {
        sinon.match({
          call_id: drivenCall.call.call_id,
          account_id: drivenCall.call.account_id,
          status: "in-progress",
          IVRStatus: {
            state: "TransferRinging",
            lastChange: sinon.match.number,
          },
        }).test(body).should.equal(true);
        return true;
      }).reply(204);


    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (res) {
        res.should.deep.equal({
          Play: {
            loop: "0",
            content: "/cloudstore/file/waitsound",
            "#name": "Play",
          },
        });
      })
      .then(function () {
        //debordement par NO_AGENT: 1/callback?overflowcause=NO_AGENT
        return drivenCall.overflowAcdLink(1, "NO_AGENT");
      })
      .then(function (body) {
        _.pick(lastEvt, ["state", "nextState"]).should.deep.equal({
          state: "Created",
          nextState: "AcdTransferred"
        });
        body.should.deep.equal({
          Redirect: {
            content: "/smartccivr/script/scriptId/node/2",
            "#name": "Redirect"
          },
        });
        return Promise.all([
          promiseStateChange("AcdTransferred", "TransferRinging"),
          drivenCall.playNodePromise(2),
        ]);
      })
      .then(function (ress) {
        var body = ress[1]; //TODO, la fixture pourrait interpreter le xml
        body.should.deep.equal({
          Dial: {
            from: "to",
            waitingurl: "/smartccivr/twimlets/loopPlay/54cf6243d658e82f077e2327",
            timeout: "10",
            statusurl: "/smartccivr/script/scriptId/dialstatus",
            callbackurl: "/smartccivr/script/scriptId/node/2/callback",
            "#name": "Dial",
            Number: {content: "0620870375", "#name": "Number"},
            // record: "false",
          },
        });
        //echec transfert sda:
        return drivenCall.dialStatus({
          dialstatus: "failed",
          dialcause: "CAU_NOR_D",
        });
      })
      .then(function () {
        return drivenCall.callbackNode(2, {
          dialstatus: "failed",
          dialcause: "CAU_NOR_D",
        });
      })
      .then(function (body) {
        body.should.deep.equal({
          HangUp: {"#name": "HangUp"},
        });
        //terminaison appel:
        return Promise.all([
          drivenCall.terminateCallStatus("xml-hangup"),
          promiseStateChange("TransferRinging", "Terminated"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (oneNock) {
          oneNock.done();
        });
        //verif tickets:
        //                                      console.log('allTickets.CALL:', allTickets.CALL);
        allTickets.CALL.length.should.equal(3);
        var callTime = allTickets.CALL[0].time;
        allTickets.CALL.forEach(function (ticket) {
          console.log("-------------- ticket", ticket);
          ticket.callTime.should.equal(callTime);
          sinon.match({
            subject: "CALL",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            time: sinon.match.number,
          }).test(ticket).should.equal(true);
        });

        sinon.match({
          state: "Created",
          nextState: "AcdTransferred",
          queueid: "queue_id1",
        }).test(allTickets.CALL[0]).should.equal(true);
        sinon.match({
          state: "AcdTransferred",
          nextState: "TransferRinging",
          acdcause: "ACD_OVERFLOW",
          overflowcause: "NO_AGENT",
          ringingSda: "0620870375",
        }).test(allTickets.CALL[1]).should.equal(true);
        sinon.match({
          state: "TransferRinging",
          nextState: "Terminated",
          cause: "IVR_HANG_UP",
          failedSda: "0620870375",
          dialcause: "failed",
          ccapi_cause: "xml-hangup",
          ccapi_dialcause: "CAU_NOR_D",
        }).test(allTickets.CALL[2]).should.equal(true);
        //                                      console.log('allTickets.ACTION:', allTickets.ACTION);
        allTickets.ACTION.length.should.equal(2);
        var actionsOnly = [];
        allTickets.ACTION.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          ticket.should.have.property("time");
          sinon.match({
            producer: "IVR",
            subject: "ACTION",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            time: sinon.match.number,
            duration: sinon.match.number,
          }).test(ticket).should.equal(true);
          actionsOnly.push(_.omit(ticket,
                                  "producer", "subject", "accountid",
                                  "applicationid", "callTime", "callid", "from",
                                  "to", "scriptid", "time", "duration"));
        });
        _.pick(actionsOnly[0], ["action"]).should.deep.equal({
          action: {type: "transferqueue", name: "mon_Transfert_Queue"},
        });
        _.pick(actionsOnly[1], ["action","endCause"]).should.deep.equal({
          action: {type: "transfersda", name: "Mon_transfert_SDA"},
          endCause: "IVR_HANG_UP",
        });
      })
      .then(done, done);
  });


  it("T02: test regression debordement transfert transfert sda -> queue", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "transfersda",
            label: "Mon transfert SDA",
            dest: "0620870375",
            case: {
              other: 2,
            },
            stat: {type: "transfersda", name: "Mon_transfert_SDA"},
          },
          2: {
            type: "transferqueue",
            queue: "queue_id1",
            stat: {type: "transferqueue", name: "Mon_transfert_QUEUE"},
          },
        },
        start: 1,
      });
    allNock.acdlink = nock(acdLinkUrl)
      .post("/call/callId/enqueue", function (body) {
        //                                      console.log('acdLink body:', body);
        sinon.match(_.omit(drivenCall.call, "status"))
          .test(body).should.equal(true);
        body.ivr_fallback.should.equal("/smartccivr/script/scriptId/node/2/callback");
        body.queue_id.should.equal("queue_id1");
        return true;
      }).reply(200, {waitSound: "waitsound"})
      .post("/call/callId/principal/status", function (body) {
        sinon.match({
          call_id: drivenCall.call.call_id,
          account_id: drivenCall.call.account_id,
          status: "completed",
          cause: "xml-hangup",
          IVRStatus: {
            state: "Terminated",
            lastChange: sinon.match.number,
          },
        }).test(body).should.equal(true);
        return true;
      }).reply(204);

    drivenCall
      .ringPromise("scriptId")
      .then(function () {
        return drivenCall.inProgressStatus();
      })
      .then(function () {
        return drivenCall.startPromise();
      })
      .then(function (res) {
        res.should.deep.equal({
          Dial: {
            from: "to",
            waitingurl: "/smartccivr/twimlets/loopPlay/54cf6243d658e82f077e2327",
            timeout: "10",
            statusurl: "/smartccivr/script/scriptId/dialstatus",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback",
            "#name": "Dial",
            Number: {content: "0620870375", "#name": "Number"},
            // record: "false",
          },
        });
        //echec transfert sda:
        return drivenCall.dialStatus({
          dialstatus: "failed",
          dialcause: "CAU_NOR_D",
        });
      })
      .then(function () {
        //callback Dial:
        return drivenCall.callbackNode(1, {
          dialstatus: "failed",
          dialcause: "CAU_NOR_D",
        });
      })
      .then(function (res) {
        res.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/2",
          },
        });
        //debordement file:
        return drivenCall.playNodePromise(2);
      })
      .then(function (res) {
        //                                      console.log('==>body', body);
        res.should.deep.equal({
          Play: {
            loop: "0",
            content: "/cloudstore/file/waitsound",
            "#name": "Play",
          },
        });
        //debordement file:
        return drivenCall.overflowAcdLink(2, "NO_AGENT");
      })
      .then(function (res) {
        res.should.deep.equal({
          HangUp: {"#name": "HangUp"},
        });
        //terminaison appel:

        return Promise.all([
          drivenCall.terminateCallStatus("xml-hangup"),
          promiseStateChange("AcdTransferred", "Terminated"),
        ]);
      })
      .then(deferPromise).then(deferPromise)
      .then(function () {
        _.each(allNock, function (oneNock) {
          oneNock.done();
        });
        //verif tickets:
        //                                      console.log('allTickets.CALL:', allTickets.CALL);
        allTickets.CALL.length.should.equal(3);
        var callTime = allTickets.CALL[0].time;
        allTickets.CALL.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          ticket.should.have.property("time");
          sinon.match({
            subject: "CALL",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
          }).test(ticket).should.equal(true);
        });

        sinon.match({
          state: "Created",
          nextState: "TransferRinging",
          ringingSda: "0620870375",
        }).test(allTickets.CALL[0]).should.equal(true);
        sinon.match({
          state: "TransferRinging",
          nextState: "AcdTransferred",
          failedSda: "0620870375",
          dialcause: "failed",
          ccapi_dialcause: "CAU_NOR_D",
          queueid: "queue_id1",
        }).test(allTickets.CALL[1]).should.equal(true);
        sinon.match({
          state: "AcdTransferred",
          nextState: "Terminated",
          cause: "IVR_HANG_UP",
          overflowcause: "NO_AGENT",
          ccapi_cause: "xml-hangup",
        }).test(allTickets.CALL[2]).should.equal(true);

        //                                      console.log("allTickets.ACTION:", allTickets.ACTION);
        allTickets.ACTION.length.should.equal(2);
        var actionsOnly = [];
        allTickets.ACTION.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          ticket.should.have.property("time");
          sinon.match({
            producer: "IVR",
            subject: "ACTION",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            time: sinon.match.number,
            duration: sinon.match.number,
          }).test(ticket).should.equal(true);
          actionsOnly.push(_.omit(ticket,
                                  "producer", "subject", "accountid",
                                  "applicationid", "callTime", "callid", "from",
                                  "to", "scriptid", "time", "duration"));
        });
        sinon.match({
          action: {type: "transfersda", name: "Mon_transfert_SDA"},
        }).test(actionsOnly[0]).should.equal(true);
        sinon.match({
          action: {type: "transferqueue", name: "Mon_transfert_QUEUE"},
          endCause: "IVR_HANG_UP",
        }).test(actionsOnly[1]).should.equal(true);

      })
      .then(done, done);
  });


  it("T03: test regression debordement queue -> queue -> son, puis abandon, test annonce interruptible", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
    //                          .log(console.log)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "transferqueue",
            queue: "queue_id1",
            stat: {type: "transferqueue", name: "Mon_transfert_QUEUE1"},
            case: {
              full: 2,
            },
          },
          2: {
            type: "transferqueue",
            queue: "queue_id2",
            stat: {type: "transferqueue", name: "Mon_transfert_QUEUE2"},
            case: {
              timeout: 3,
            },
          },
          3: {
            type: "announcement",
            label: "Mon annonce",
            soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
            stat: {type: "sonType", name: "sonName"},
          },
        },
        start: 1,
      })
      .get("/account/accountId/file")
      .query(true)
      .once()
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "otisReddingId"}]});

    allNock.acdlink = nock(acdLinkUrl)
    //                          .log(console.log)
      .post("/call/callId/enqueue", function (body) {
        //                                      console.log('acdLink body:', body);
        sinon.match({
          call_id: "callId",
          account_id: "accountId",
          application_id: "appId",
          from: "from",
          to: "to",
          ivr_fallback: "/smartccivr/script/scriptId/node/1/callback",
          queue_id: "queue_id1",
          callTime: sinon.match.number,
        }).test(body).should.equal(true);
        return true;
      }).once().reply(200, {waitSound: "waitsound1"});

    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (scenario) {
        scenario.should.deep.equal({
          Play: {
            loop: "0",
            content: "/cloudstore/file/waitsound1",
            "#name": "Play",
          },
        });
        //debordement vers une autre file:
        return drivenCall.overflowAcdLink(1, "FULL_QUEUE");
      })
      .then(function (sce) {
        sce.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/2",
          }, //TODO check si la resolution est OK (cf tests Integration)
        });
        //l ivr appelara l acdLink:
        allNock.acdlink.post("/call/callId/enqueue", function (body) {
          //                                    console.log('acdLink body:', body);
          sinon.match({
            call_id: "callId",
            account_id: "accountId",
            application_id: "appId",
            from: "from",
            to: "to",
            ivr_fallback: "/smartccivr/script/scriptId/node/2/callback",
            queue_id: "queue_id2",
            callTime: sinon.match.number,
          }).test(body).should.equal(true);
          return true;
        }).once().reply(200, {waitSound: "waitsound2"});
        return drivenCall.playNodePromise(2);
      })
      .then(function (sce) {
        //acdLink a demandé à jouer un son:
        sce.should.deep.equal({
          Play: {
            loop: "0",
            content: "/cloudstore/file/waitsound2",
            "#name": "Play",
          },
        });
        //debordement QUEUE_TIMEOUT:
        allNock.acdlink
          .post("/call/callId/principal/status", function (body) {
            sinon.match({
              call_id: drivenCall.call.call_id,
              account_id: drivenCall.call.account_id,
              status: "in-progress",
              IVRStatus: {
                state: "InProgress",
                lastChange: sinon.match.number,
              },
            }).test(body).should.equal(true);
            return true;
          }).reply(204);

        return drivenCall.overflowAcdLink(2, "QUEUE_TIMEOUT");
      })
      .then(function (ress) { //cette partie peut être refactorée
        //redirction vers le son:
        ress.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/3",
          }, //TODO : resolve in DrivenCall
        });
        //debordement noeud3:
        return drivenCall.playNodePromise(3);
      })
      .then(function (res) {
        res.should.deep.equal({
          Gather: {
            Play: {
              content: "/cloudstore/file/otisReddingId",
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
        //abandon en annonce:
        return Promise.all([
          drivenCall.terminateCallStatus("user-hangup"),
          promiseStateChange("InProgress", "Terminated"), //terminaison appel par les ticket IVR
        ]);
      })
      .then(function () {
        _.each(allNock, function (oneNock) {
          oneNock.done();
        });
        //verif tickets:
        //                                      console.log('allTickets.CALL:', allTickets.CALL);
        allTickets.CALL.length.should.equal(3);
        var callTime = allTickets.CALL[0].time;
        var sviTickets = [];
        allTickets.CALL.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          sinon.match({
            subject: "CALL",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            duration: sinon.match.number,
            time: sinon.match(timestampChecker),
          }).test(ticket).should.equal(true);
          sviTickets.push(_(ticket).omit(
            "producer", "subject", "accountid", "applicationid",
            "callid", "from", "to", "scriptid", "time", "callTime", "duration"));
        });

        sinon.match({
          state: "Created",
          nextState: "AcdTransferred",
          queueid: "queue_id1",
        }).test(sviTickets[0]).should.equal(true);
        sinon.match({
          state: "AcdTransferred",
          nextState: "InProgress",
          acdcause: "ACD_OVERFLOW",
          overflowcause: "QUEUE_TIMEOUT",
        }).test(sviTickets[1]).should.equal(true);
        sinon.match({
          state: "InProgress",
          nextState: "Terminated",
          cause: "CALLER_HANG_UP",
        }).test(sviTickets[2]).should.equal(true);

        //                                      console.log("allTickets.ACTION:", allTickets.ACTION);
        allTickets.ACTION.length.should.equal(3);
        var actionsOnly = [];
        allTickets.ACTION.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          sinon.match({
            producer: "IVR",
            subject: "ACTION",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            time: sinon.match(timestampChecker),
            duration: sinon.match.number,
          }).test(ticket).should.equal(true);
          actionsOnly.push(_.omit(ticket,
                                  "producer", "subject", "accountid",
                                  "applicationid", "callTime", "callid", "from",
                                  "to", "scriptid", "time", "duration"));
        });
        sinon.match({
          action: {type: "transferqueue", name: "Mon_transfert_QUEUE1"},
        }).test(actionsOnly[0]).should.equal(true);
        sinon.match({
          action: {type: "transferqueue", name: "Mon_transfert_QUEUE2"},
        }).test(actionsOnly[1]).should.equal(true);
        sinon.match({
          action: {type: "sonType", name: "sonName"},
          endCause: "CALLER_HANG_UP",
        }).test(actionsOnly[2]).should.equal(true);

      })
      .then(done, done);
  });

  it("T04: test regression annonce -> queue , puis abandon en attente", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
    //                          .log(console.log)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "announcement",
            label: "Mon annonce",
            soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
            stat: {type: "sonType", name: "sonName"},
            next: 2,
          },
          2: {
            type: "transferqueue",
            queue: "queue_id1",
            stat: {type: "transferqueue", name: "Mon_transfert_QUEUE1"},
          },
        },
        start: 1,
      })
      .get("/account/accountId/file")
      .query(true)
      .once()
      .reply(200, {meta: {total_count: 1}, objects: [{_id: "otisReddingId"}]});

    allNock.acdlink = nock(acdLinkUrl)
    //                          .log(console.log)
      .post("/call/callId/enqueue", function (body) {
        //                                      console.log('acdLink body:', body);
        sinon.match({
          call_id: "callId",
          account_id: "accountId",
          application_id: "appId",
          from: "from",
          to: "to",
          ivr_fallback: "/smartccivr/script/scriptId/node/2/callback",
          queue_id: "queue_id1",
          callTime: sinon.match.number,
        }).test(body).should.equal(true);
        return true;
      }).once().reply(200, {waitSound: "waitsound1"});

    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (scenario) {
        scenario.should.deep.equal({
          Gather: {
            Play: {
              content: "/cloudstore/file/otisReddingId",
              "#name": "Play",
            },
            "#name": "Gather",
            callbackmethod: "POST",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback",
            numdigits: "1",
            timeout: "1",
          },
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/2",
          },
        });
        //next : 2
        return drivenCall.playNodePromise(2);
      })
      .then(function (sce) {
        sce.should.deep.equal({
          Play: {
            loop: "0",
            content: "/cloudstore/file/waitsound1",
            "#name": "Play",
          }, //TODO check si la resolution est OK (cf tests Integration)
        });

        //abandon pdt l attente
        //l ivr appelara l acdLink pour propager le status:
        allNock.acdlink
          .post("/call/callId/principal/status", function (body) {
            sinon.match({
              call_id: drivenCall.call.call_id,
              account_id: drivenCall.call.account_id,
              status: "completed",
              cause: "user-hangup",
              IVRStatus: {
                state: "Terminated",
                lastChange: sinon.match(timestampChecker),
              },
            }).test(body).should.equal(true);
            return true;
          }).reply(204);

        return Promise.all([
          drivenCall.terminateCallStatus("user-hangup"),
          promiseStateChange("AcdTransferred", "Terminated"),
        ]);
      })
      .then(function () {
        return new Promise((resolve) => {
          setTimeout(resolve, 100);
        });
      })
      .then(function () {
        _.each(allNock, function (oneNock) {
          oneNock.done();
        });
        //verif tickets:
        //                                      console.log('allTickets.CALL:', allTickets.CALL);
        allTickets.CALL.length.should.equal(3);
        var callTime = allTickets.CALL[0].time;
        var sviTickets = [];
        allTickets.CALL.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          sinon.match({
            subject: "CALL",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            duration: sinon.match.number,
            time: sinon.match(timestampChecker),
          }).test(ticket).should.equal(true);
          sviTickets.push(_(ticket).omit(
            "producer", "subject", "accountid", "applicationid",
            "callid", "from", "to", "scriptid", "time", "callTime", "duration"));
        });

        sinon.match({
          state: "Created",
          nextState: "InProgress",
        }).test(sviTickets[0]).should.equal(true);
        sinon.match({
          state: "InProgress",
          nextState: "AcdTransferred",
        }).test(sviTickets[1]).should.equal(true);
        sinon.match({
          state: "AcdTransferred",
          nextState: "Terminated",
        }).test(sviTickets[2]).should.equal(true);

      })
      .then(done, done);
  });


  it("T05: test regression liste vide -> queue puis dissuasion en attente", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
    //                          .log(console.log)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "transferlist",
            label: "Mon transfert",
            dest: "list_id",
            stat: {type: "transf1", name: "name1"},
            failover: 2,
          },
          2: {
            type: "transferqueue",
            queue: "queue_id42",
            stat: {type: "transferqueue", name: "Mon_transfert_QUEUE1"},
          },
        },
        start: 1,
      });
    allNock.ivrservices = nock(ivrServicesUrl)
      .post("/account/accountId/destinationlist/list_id/eval", {})
      .once().reply(404, {status: 404, apiCode: "no_sda_found"});

    allNock.acdlink = nock(acdLinkUrl)
      .post("/call/callId/enqueue", function (body) {
        //                                      console.log("acdLink body:", body);
        sinon.match({
          call_id: "callId",
          account_id: "accountId",
          application_id: "appId",
          from: "from",
          to: "to",
          ivr_fallback: "/smartccivr/script/scriptId/node/2/callback",
          queue_id: "queue_id42",
          callTime: sinon.match(timestampChecker),
        }).test(body).should.equal(true);
        return true;
      }).once().reply(200, {waitSound: "waitsound1"})
      .post("/call/callId/principal/status", function (body) {
        sinon.match({
          call_id: drivenCall.call.call_id,
          account_id: drivenCall.call.account_id,
          status: "completed",
          cause: "xml-hangup",
          IVRStatus: {
            state: "Terminated",
            lastChange: sinon.match(timestampChecker),
          },
        }).test(body).should.equal(true);
        return true;
      }).reply(204);

    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (scenario) {
        scenario.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/2",
          },
        });
        return drivenCall.playNodePromise(2);
      })
      .then(function (sce) {
        sce.should.deep.equal({
          Play: {
            "#name": "Play",
            loop: "0",
            content: "/cloudstore/file/waitsound1",
          },
        });
        return drivenCall.overflowAcdLink(2, "FULL_QUEUE");
      })
      .then(function (sce) {
        sce.should.deep.equal({
          HangUp: {
            "#name": "HangUp",
          },
        });
        return Promise.all([
          drivenCall.terminateCallStatus("xml-hangup"),
          promiseStateChange("AcdTransferred", "Terminated")]);
      })
      .then(function () {
        return new Promise((resolve) => {
          setTimeout(resolve, 100);
        });
      })
      .then(function () {

        _.each(allNock, function (oneNock) {
          oneNock.done();
        });

        allTickets.CALL.length.should.equal(2);
        var callTime = allTickets.CALL[0].time;
        var sviTickets = [];
        allTickets.CALL.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          sinon.match({
            subject: "CALL",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            duration: sinon.match.number,
            time: sinon.match(timestampChecker),
          }).test(ticket).should.equal(true);
          sviTickets.push(_(ticket).omit(
            "producer", "subject", "accountid", "applicationid",
            "callid", "from", "to", "scriptid", "time", "callTime", "duration"));
        });

        sinon.match({
          state: "Created",
          queueid: "queue_id42",
          nextState: "AcdTransferred",
        }).test(sviTickets[0]).should.equal(true);
        sinon.match({
          state: "AcdTransferred",
          acdcause: "ACD_OVERFLOW",
          overflowcause: "FULL_QUEUE",
          cause: "IVR_HANG_UP",
          ccapi_cause: "xml-hangup",
          nextState: "Terminated",
        }).test(sviTickets[1]).should.equal(true);

        allTickets.ACTION.length.should.equal(2);
        var actionsOnly = [];
        allTickets.ACTION.forEach(function (ticket) {
          ticket.callTime.should.equal(callTime);
          sinon.match({
            producer: "IVR",
            subject: "ACTION",
            accountid: "accountId",
            applicationid: "appId",
            callid: "callId",
            from: "from",
            to: "to",
            scriptid: "scriptId",
            time: sinon.match(timestampChecker),
            duration: sinon.match.number,
          }).test(ticket).should.equal(true);
          actionsOnly.push(_.omit(ticket,
                                  "producer", "subject", "accountid",
                                  "applicationid", "callTime", "callid", "from",
                                  "to", "scriptid", "time", "duration"));
        });
        sinon.match({
          action: {type: "transf1", name: "name1"},
        }).test(actionsOnly[0]).should.equal(true);
        sinon.match({
          action: {type: "transferqueue", name: "Mon_transfert_QUEUE1"},
          endCause: "IVR_HANG_UP",
        }).test(actionsOnly[1]).should.equal(true);
        done();
      })
      .catch(done);
  });


  it("T06: test regression debordement transfert transfert sda -> sda -> Route (ajout dialcause)", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        _id: "scriptId",
        nodes: {
          1: {
            type: "transfersda",
            label: "Mon transfert SDA",
            dest: "0620870375",
            case: {
              other: 2,
            },
          },
          2: {
            type: "transfersda",
            label: "Mon transfert SDA v2",
            dest: "0620870376",
            case: {
              other: 3,
            },
          },
          3: {
            type: "route",
            varname: "CALLER",
            case: {},
          },
        },
        start: 1,
      });


    drivenCall
      .ringPromise("scriptId")
      .then(function () {
        return drivenCall.inProgressStatus();
      })
      .then(function () {
        return drivenCall.startPromise();
      })
      .then(function (res) {
        res.should.deep.equal({
          Dial: {
            from: "to",
            waitingurl: "/smartccivr/twimlets/loopPlay/54cf6243d658e82f077e2327",
            timeout: "10",
            statusurl: "/smartccivr/script/scriptId/dialstatus",
            callbackurl: "/smartccivr/script/scriptId/node/1/callback",
            "#name": "Dial",
            Number: {content: "0620870375", "#name": "Number"},
            // record: "false",
          },
        });
        //echec transfert sda:
        return drivenCall.dialStatus({
          dialstatus: "failed",
          dialcause: "CAU_NOR_D",
        });
      })
      .then(function () {
        //callback Dial:
        return drivenCall.callbackNode(1, {
          dialstatus: "failed",
          dialcause: "CAU_NOR_D",
        });
      })
      .then(function (res) {
        res.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/2",
          },
        });
        //debordement file:
        return drivenCall.playNodePromise(2);
      })
      .then(function (res) {
        res.should.deep.equal({
          Dial: {
            from: "to",
            waitingurl: "/smartccivr/twimlets/loopPlay/54cf6243d658e82f077e2327",
            timeout: "10",
            statusurl: "/smartccivr/script/scriptId/dialstatus",
            callbackurl: "/smartccivr/script/scriptId/node/2/callback",
            "#name": "Dial",
            Number: {content: "0620870376", "#name": "Number"},
            // record: "false",
          },
        });
        //echec transfert sda:
        return drivenCall.dialStatus({
          dialstatus: "failed",
          dialcause: "CAU_NO_ANSW",
        });
      })
      .then(function () {
        //callback Dial:
        return drivenCall.callbackNode(2, {
          dialstatus: "failed",
          dialcause: "CAU_NO_ANSW",
        });
      })
      .then(function (res) {
        res.should.deep.equal({
          Redirect: {
            "#name": "Redirect",
            content: "/smartccivr/script/scriptId/node/3",
          },
        });
        //debordement file:
        return drivenCall.playNodePromise(3);
      })
      .then(function (res) {
        res.should.deep.equal({
          HangUp: {"#name": "HangUp"},
        });
        //terminaison appel:

        return Promise.all([
          drivenCall.terminateCallStatus("xml-hangup"),
          promiseStateChange("InProgress", "Terminated"),
        ]);
      })
      .then(function () {
        _.each(allNock, function (oneNock) {
          oneNock.done();
        });
        //verif tickets:
        //                                      console.log('allTickets.CALL:', allTickets.CALL);
        allTickets.CALL.length.should.equal(4);

        sinon.match({
          state: "Created",
          nextState: "TransferRinging",
          ringingSda: "0620870375",
        }).test(allTickets.CALL[0]).should.equal(true);
        sinon.match({
          state: "TransferRinging",
          nextState: "TransferRinging",
          failedSda: "0620870375",
          dialcause: "failed",
          ccapi_dialcause: "CAU_NOR_D",
          ringingSda: "0620870376",
        }).test(allTickets.CALL[1]).should.equal(true);
        sinon.match({
          state: "TransferRinging",
          nextState: "InProgress",
          failedSda: "0620870376",
          dialcause: "failed",
          ccapi_dialcause: "CAU_NO_ANSW",
        }).test(allTickets.CALL[2]).should.equal(true);
        sinon.match({
          state: "InProgress",
          nextState: "Terminated",
          cause: "IVR_HANG_UP",
          ccapi_cause: "xml-hangup",
        }).test(allTickets.CALL[3]).should.equal(true);

      })
      .then(done, done);
  });

  it("T07: test regression script avec une liste vide", function (done) {
    var allNock = {};
    allNock.cloudstore = nock(cloudStoreUrl)
      .persist()
      .get("/account/accountId/script/scriptId")
      .reply(200, {
        label: "test transfert list",
        state: "DEV",
        nodes: {
          1: {
            type: "transferlist",
            label: "mon transfert liste",
            stat: {type: "transferlist", name: "mon transfert liste"},
            dest: "listid1",
          },
        },
        start: 1,
        account_id: "accountId",
        created_at: "2016-03-17T17:19:17.001Z",
        _id: "scriptId",
      });

    allNock.ivr_services = nock(ivrServicesUrl)
      .post("/account/accountId/destinationlist/listid1/eval")
      .once()
      .reply(404, {status: 404, apiCode: "no_sda_found"});

    drivenCall
      .ringInProgressAndStartPromise("scriptId")
      .then(function (res) {
        res.should.deep.equal({
          HangUp: {"#name": "HangUp"},
        });
        return Promise.all([
          drivenCall.terminateCallStatus("xml-hangup"),
        ]);
      })
      .then(deferPromise)
      .then(function () {
        _.each(allNock, function (oneNock) {
          oneNock.done();
        });
        //verif tickets:
        allTickets.CALL.length.should.equal(0);
        allTickets.ACTION.length.should.equal(0);
      })
      .then(done, done);
  });


});
