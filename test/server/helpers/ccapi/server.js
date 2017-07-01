"use strict";

module.exports = Server;
var http = require("http");
var express = require("express");
var callmanager = require("./CallManager");
// var cloudTools = require("CloudTools");
var bodyparser = require("body-parser");
var merge = require("merge");
var logger, options;

function Server(_options, callback) {
  var me = this;
  var formParser = bodyparser.urlencoded({extended: false});

  // cloudTools.initializeModule.addListener(function () {
  //   logger = cloudTools.loggerManager.getLogger("CCAPISimulator");
  //   options = cloudTools.configService.getConfig().ccapi_mock;
  options = _options.ccapi_mock;
  callmanager.options(options);
  console.log(">>>>>>>>>>>>>>>>>>>><init server CCAPI simulation", options);
  var app = express();
  var callMiddleware = function (opt) {
    return    function (req, res, next) {
      var params = merge(true, req.body, req.query, req.params);
      if (!callmanager[opt]) {
        console.error("Appel d une methode inexistant:", opt);
      }

      callmanager[opt](params, function (err, data) {
        if (err) {
          console.error("==========================middle error", err);
          return next(err);
        }

        if (data) {
          res.status(201).json(data);
        } else {
          res.status(204).end();
        }
      });
    };
  };

  //creation d un appel
  app.post("/ccapimock/Accounts/:account_id/Calls",
           formParser, callMiddleware("createCall"));
  //update d un appel
  app.put("/ccapimock/Accounts/:account_id/Calls/:call_id",
          formParser, callMiddleware("updateCall"));
  //simulation de l utilisateur qui racroche
  app.post("/ccapimock/callcontrol/:call_id/user_hangup",
           formParser, callMiddleware("userHangUp"));
  app.post("/ccapimock/callcontrol/:call_id/dial_leg_b_hangup",
           formParser, callMiddleware("dialLegBHangUp"));

  // Gestion des erreurs
  app.use(function (err, req, res) {
    if (!err.statusCode) {
      err.statusCode = 500;
    }
    res.status(err.statusCode).json({
      code: err.statusCode,
      description: err.message,
      error: err.name,
    });
  });

  me.server = http.createServer(app);

  // });
  callback();
}

/*lancement du serveur*/
Server.prototype.start = function (callback) {
  this.stopped = false;
  this.server.listen(options.port, callback);
};


/*arret du serveur*/
Server.prototype.stop = function (callback) {
  callmanager.clearCalls();
  if (!this.stopped) {
    this.stopped = true;
    this.server.close(callback);
  } else {
    callback();
  }
};
