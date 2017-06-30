/* global require*/
"use strict";

/*
 *fixture pour externaliser les appels CCAPI (callback,status,XML interpreters) pour les TR & TU
 */


var supertestPromised = require("supertest-as-promised");
var _ = require("underscore");
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

var parseStringOrder = new xml2js.Parser({
  trim: true,
  explicitRoot: false,
  explicitArray: false,
  explicitChildren: true,
  preserveChildrenOrder: true,
  charkey: "content",
  childkey: "childreen",
  attrkey: "attrs",
}).parseString;

function prune(val) {
  if (!val["#name"])    {
    return val;
  }
  if (val.childreen)    {
    return {
      "#name": val["#name"],
      attrs: val.attrs,
      childreen: val.childreen.map(prune),
    };
  }
  return val;
}

module.exports = DrivenCall;


function DrivenCall(url) {
  this.ivr_url = url;
  this.call = {
    call_id: "callId",
    account_id: "accountId",
    application_id: "appId",
    from: "from",
    to: "to",
    status: "ringing", //pas utile
  };
  this.callUrl = `/account/${ this.call.account_id }/call/${ this.call.call_id}`;
}

DrivenCall.prototype.parseXMlRes = function (httpRes) {
  var me = this;
  me.lastScenario = null;
  return new Promise(function (resolve, reject) {
    if (me.orderedParse)                {
      parseStringOrder(httpRes.text, function (err, res) {
        if (err)                                {
          return reject(err);
        }

        resolve(prune(res).childreen);
      });
    } else              {
      parseString(httpRes.text, function (err, res) {
        return err ? reject(err) : resolve(_.omit(res, "#name"));
      });
    }
  }).then(function (jsonSce) {
    me.lastScenario = jsonSce;
    return jsonSce;
  });
};

DrivenCall.prototype.ringInProgressAndStartPromise = function (scriptid) {
  var me = this;
  return this.ringPromise(scriptid)
    .then(function () {
      return me.inProgressStatus();
    })
    .then(function () {
      return me.startPromise();
    });
};
DrivenCall.prototype.ringPromise = function (scriptid) {
  this.scriptid = scriptid;
  this.statusUrl = `/script/${ scriptid }/status`;
  this.scriptBaseUrl = `/script/${ scriptid}`;
  return supertestPromised(this.ivr_url)
    .post(this.statusUrl)//US 769: sonnerie avant
    .type("form")
    .send(this.call) //sonnerie appel
    .expect(204);
};
DrivenCall.prototype.startPromise = function () {
  return this.playNodePromise("start");
};
DrivenCall.prototype.playNodePromise = function (nodeid) {
  return supertestPromised(this.ivr_url)
    .post(`/script/${ this.scriptid }/node/${ nodeid}`)
    .type("form")
    .send(this.call) //arrivé d un appel:
    .expect(200)
    .then(this.parseXMlRes.bind(this));
};

DrivenCall.prototype.inProgressStatus = function () {
  this.call.status = "in-progress";//envoie du status
  return supertestPromised(this.ivr_url)
    .post(this.statusUrl)
    .type("form")
    .send(this.call)
    .expect(204);
};

DrivenCall.prototype.overflowAcdLink = function (nodeid, cause) {
  return supertestPromised(this.ivr_url)
    .post(`${this.scriptBaseUrl }/node/${ nodeid }/callback?overflowcause=${ cause}`)
    .send(this.call)
    .expect(200)
    .then(this.parseXMlRes.bind(this));
};

DrivenCall.prototype.dialStatus = function (dialParams) {
  return supertestPromised(this.ivr_url)
    .post(`${this.scriptBaseUrl }/dialstatus`)
    .send(_.extend({}, this.call, dialParams))
    .expect(204);
};

DrivenCall.prototype.callbackNode = function (nodeid, dialParams) {
  return supertestPromised(this.ivr_url)
    .post(`${this.scriptBaseUrl }/node/${ nodeid }/callback`)
    .send(_.extend({}, this.call, dialParams))
    .expect(200)
    .then(this.parseXMlRes.bind(this));
};

DrivenCall.prototype.terminateCallStatus = function (cause) {
  this.call.status = "completed";
  this.call.cause = cause;
  return supertestPromised(this.ivr_url)
    .post(this.statusUrl)
    .send(this.call)
    .expect(204);
};

DrivenCall.prototype.cancelCallStatus = function (cause) {
  this.call.status = "canceled";
  this.call.cause = cause;
  return supertestPromised(this.ivr_url)
    .post(this.statusUrl)
    .send(this.call)
    .expect(204);
};

DrivenCall.prototype.getbusinessCtx = function () {
  return supertestPromised(this.ivr_url)
    .get(this.callUrl)
    .expect(200);
};
