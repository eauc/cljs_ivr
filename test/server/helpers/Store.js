"use strict";

// var cloudTools = require("CloudTools");
var nock = require("nock");
var _ = require("underscore");

// var logger;

// cloudTools.initializeModule.addListener(function () {
//   logger = cloudTools.loggerManager.getLogger("Store");
// });

var scripts = {
  script42: {
    label: "script mc, NEPAS TOUCHER MANU",
    state: "DEV",
    nodes: {
      1: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonce",
        soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
        next: 3,
      },
      3: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonceV2",
        soundname: "Meilleur_sonnerie_pour_portable_2016.ogg",
      },
    },
    start: 1,
    account_id: "accountid1",
    created_at: "2016-03-17T17:19:17.001Z",
    _id: "script42",
  },
  script69: {
    label: "script mc, NEPAS TOUCHER MANU2",
    state: "DEV",
    nodes: {
      1: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonce",
        soundname: "autre.ogg",
        next: 3,
      },
      3: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonceV2",
        soundname: "autre.ogg",
        next: 3,
      },
    },
    start: 1,
    account_id: "accountid1",
    created_at: "2016-03-17T17:19:17.001Z",
    _id: "script69",
  },
  script634: {
    _id: "script634",
    label: "test demo",
    description: "test",
    nodes: {
      1: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "0620870375",
        case: {
          noanswer: 2,
        },
      },
      2: {
        type: "announcement",
        label: "mon annonce2",
        soundname: "zic2",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script634_2: {
    _id: "script634_2",
    label: "test demo",
    description: "test",
    nodes: {
      1: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "no-answerSDA",
        case: {
          noanswer: 2,
        },
      },
      2: {
        type: "announcement",
        no_barge: true,
        label: "mon annonce2",
        soundname: "zic2",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script634_3: {
    _id: "script634_3",
    label: "test demo",
    description: "test",
    nodes: {
      1: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "busySDA",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script634_4: {
    _id: "script634_4",
    label: "test demo",
    description: "test",
    nodes: {
      1: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "busySDA",
        case: {
          busy: 2,
        },
      },
      2: {
        type: "announcement",
        no_barge: true,
        label: "mon annonce2",
        soundname: "zic2",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script634_5: {
    _id: "script634_5",
    label: "test demo",
    description: "test",
    nodes: {
      1: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "busySDA",
        case: {
          busy: 2,
        },
      },
      2: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "no-answerSDA",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script633: {
    _id: "script633",
    label: "test demo",
    description: "test",
    nodes: {
      1: {
        type: "dtmfcatch",
        label: "mon catch",
        welcome: {
          soundname: "zic2",
        },
        numdigits: 4,
        finishonkey: "*",
        timeout: 5,
        max_attempts: 4,
        varname: "DTMF_42",
        case: {
          dtmf_ok: 3,
          max_attempt_reached: 5,
        },
      },
      2: {
        type: "dtmfcatch",
        label: "mon catch",
        welcome: {
          soundname: "zic2",
        },
        numdigits: 4,
        finishonkey: "*",
        timeout: 5,
        max_attempts: 4,
        varname: "DTMF_42",
      },
      3: {
        type: "dtmfcatch",
        label: "mon catch",
        welcome: {
          soundname: "zic2",
        },
        timeout: 7,
        max_attempts: 4,
        varname: "DTMF_42",
      },
      4: {
        type: "dtmfcatch",
        label: "mon catch",
        welcome: {
          soundname: "zic2",
        },
        timeout: 7,
        max_attempts: 4,
        varname: "DTMF_42",
        numdigits: 1,
        validationpattern: "^[12]$",
        case: {
          dtmf_ok: 3,
          max_attempt_reached: 5,
        },
      },
      5: {
        type: "dtmfcatch",
        label: "mon catch",
        welcome: [{soundname: "zic3"}, {soundname: "zic2"}],
        numdigits: 4,
        finishonkey: "*",
        timeout: 5,
        max_attempts: 4,
        varname: "DTMF_42",
        case: {
          dtmf_ok: 3,
          max_attempt_reached: 5,
        },
      },
      6: {
        type: "dtmfcatch",
        label: "mon catch",
        welcome: [{soundname: "zic3"}, {soundname: "zic2"}],
        timeout: 7,
        max_attempts: 4,
        varname: "DTMF_42",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script656: {
    _id: "script656",
    label: "test queue transfert",
    description: "test",
    nodes: {
      1: {
        type: "transferqueue",
        queue: "ma_queue_id",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script656_2: {
    _id: "script656_2",
    label: "test queue transfert",
    description: "test",
    nodes: {
      1: {
        type: "transferqueue",
        queue: "queue_id2",
        case: {
          noagent: 2,
        },
      },
      3: {
        type: "transferqueue",
        queue: "queue_id2",
        case: {
          timeout: 2,
        },
      },
      2: {
        type: "announcement",
        no_barge: true,
        label: "mon annonce2",
        soundname: "zic2",
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script656_3: {
    _id: "script656_3",
    label: "test queue transfert",
    description: "test",
    nodes: {
      1: {
        type: "transferqueue",
        queue: "queue_id2",
        case: {
          noagent: 2,
        },
      },
      3: {
        type: "transferqueue",
        queue: "queue_id2",
        case: {
          timeout: 2,
        },
      },
      2: {
        type: "announcement",
        no_barge: true,
        label: "mon annonce2",
        soundname: "zic2",
      },
    },
    start: 3,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script666: {
    _id: "script666",
    label: "stats_action",
    description: "test stats action",
    nodes: {
      1: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonce",
        soundname: "autre.ogg",
        next: 2,
        stat: {type: "announcement", name: "Mon annonce"},
      },
      2: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonce 2 ",
        soundname: "autre.ogg",
        next: 3,
      },
      3: {
        type: "transfersda",
        label: "Mon transfert SDA",
        dest: "0620870375",
        stat: {type: "transfersda", name: "Mon transfert SDA"},
      },
    },
    start: 1,
    state: "DEV",
    account_id: "accountid1",
    created_at: "2016-04-07T12:00:20.749Z",
    updated_at: "2016-04-07T15:53:22.791Z",
  },
  script739: {
    _id: "script739",
    label: "test getBusinessContext",
    state: "DEV",
    nodes: {
      2: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonce",
        soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
        next: 2,
      },
      1: {
        type: "fetch",
        label: "TU SMARTCC-739, node fetch 2",
        id_routing_rule: "routingrule_id",
        varname: "VarContext2",
        next: 2,
      },
    },
    start: 1,
    account_id: "accountid1",
    created_at: "2016-03-17T17:19:17.001Z",
  },
  script684: {
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
    account_id: "accountid1",
    created_at: "2016-03-17T17:19:17.001Z",
    _id: "script684",
  },
  script684_1: {
    label: "test transfert list",
    state: "DEV",
    nodes: {
      1: {
        type: "transferlist",
        label: "mon transfert liste",
        dest: "listid1",
        stat: {type: "transferlist", name: "mon transfert liste"},
        failover: 2,
      },
      2: {
        type: "announcement",
        no_barge: true,
        label: "Mon annonce",
        stat: {type: "announcement", name: "Mon annonce"},
        soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
      },
    },
    start: 1,
    account_id: "accountid1",
    created_at: "2016-03-17T17:19:17.001Z",
    _id: "script684_1",
  },
  script637: {
    _id: "script637",
    nodes: {
      1: {
        cancelKey: "*",
        validateKey: "4",
        type: "voicerecord",
        case: {
          cancel: 2,
          validate: {next: 3, set: {varname: "voicerecorde_1_smtp_ok"}},
        },
        varname: "var_name_4_to_5",
      },
      2: {
        type: "announcement",
        label: "Mon annonce",
        soundname: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
      },
      3: {
        type: "announcement",
        label: "Mon annonce",
        soundname: "autre.ogg",
        stat: {type: "announcement", name: "Mon annonce"},
      },
    },
    start: 1,
  },
  script637_2: {
    _id: "script637_2",
    nodes: {
      1: {
        cancelKey: "#",
        validateKey: "4",
        type: "voicerecord",
        varname: "var_name_4_to_5",
      },
    },
    start: 1,
  },
};

module.exports.scripts = scripts;

var confServer = {
  ccapi_mock: {//conf du mock CCAPI
    playingTime: 100,
    ringingTime: 100,
    port: 8086,
  },
  port: 8085, //port d ecoute de SmartCCIVR
  environment: {
    dispatch_url: {
      internal: "http://ic.test.smartcc:8080",
    }
  },
  business: {
    transfersda: {
      ringing_tone: "54cf6243d658e82f077e2327",
      ringingTimeoutSec: 10,
    },
  },
  zmq: {
    activeZMQ: false,
  },
};
module.exports.confServer = confServer;

module.exports.nockStoreScript42 = function () {
  return nock(`${confServer.environment.dispatch_url.internal }/cloudstore/account/accountid1`)
    .log(console.log)
    .get("/script/script42")
    .twice()
    .reply(200, scripts.script42)
    .get("/file")
    .once()
    .query({
      query: {
        filename: "Otis_Redding-Sitting_on_the_dock_of_the_bay.ogg",
        "metadata.type": "scriptSound",
        "metadata.script": "script42",
      },
    })
    .reply(200, {meta: {total_count: 1}, objects: [{_id: "otisIdFile"}]})
    .get("/file")
    .once()
    .query({
      query: {
        filename: "Meilleur_sonnerie_pour_portable_2016.ogg",
        "metadata.type": "scriptSound",
        "metadata.script": "script42",
      },
    })
    .reply(200, {meta: {total_count: 1}, objects: [{_id: "sonnerieIdFile"}]});
};

module.exports.nockScript = function (scriptName) {
  return nock(`${confServer.environment.dispatch_url.internal }/cloudstore/account/accountid1`)
    .log(console.log)
    .persist()
    .get(`/script/${ scriptName}`)
    .reply(200, scripts[scriptName]);
};

module.exports.nockAllScripts = function () {
  var lnock = nock(`${confServer.environment.dispatch_url.internal }/cloudstore/account/accountid1`)
      .log(console.log)
    .persist();
  _.each(scripts, function (value, key) {
    lnock = lnock.get(`/script/${ key}`)
      .reply(200, value);
  });
  return lnock;
};

module.exports.nockAllSounds = function () {
  return nock(`${confServer.environment.dispatch_url.internal }/cloudstore/account/accountid1`)
    .log(console.log)
    .persist()
    .get("/file")
    .query(true)
    .reply(200, {meta: {total_count: 1}, objects: [{_id: "autreSoundId"}]});
};

var callsOnSda = {};
module.exports.nockLimitManager = function () {
  callsOnSda = {};
  var lnock = nock(`${confServer.environment.dispatch_url.internal }/cloudmemory/counter`)
    .log(console.log)
    .persist();

  lnock.get(/.*incr/)
    .reply(function (uri) {
      //                                console.log('============', uri);
      var sda = uri.split("/")[3];
      callsOnSda[sda] = callsOnSda[sda] ? callsOnSda[sda] + 1 : 1;
      return [
        200, {response: "ok"},
      ];
    });
  lnock.get(/.*decr/)
    .reply(function (uri) {
      //                                console.log('============', uri);
      var sda = uri.split("/")[3];
      callsOnSda[sda] = callsOnSda[sda] - 1;
      return [
        200, {response: "ok"},
      ];
    });
};
module.exports.getCallOnSda = function (sda) {
  return callsOnSda[sda];
};
