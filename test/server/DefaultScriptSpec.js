"use strict";

const hippie = require("hippie-swagger");
const SwaggerParser = require("swagger-parser");
const expect = require("chai").expect;
const server = require("./helpers/server.js");

describe("Script par défaut", function () {

  before(function () {
    this.rawParser = (body, fn) => {
      fn(null, body);
    };
    return SwaggerParser.dereference("swagger/swagger.json").then((api) => {
      this.swagger = api;
    });
  });

  describe("Si on veut jouer le son de bienvenue", function () {

    beforeEach(function () {
      this.confServer = {
        port: 8085,
        business: {
          welcome_sound: "mySoundId",
        },
      };
      return server.start(this.confServer);
    });

    afterEach(function () {
      server.stop();
    });

    it("la demande doit retourner un twimlet play du son", function () {
      return hippie(this.swagger)
        .parser(this.rawParser)
        .base(`http://localhost:${this.confServer.port}/smartccivr`)
        .get("/twimlets/welcome")
        .expectHeader("Content-Type", "application/xml; charset=utf-8")
        .expectStatus(200)
        .expect((res, body, next) => {
          const trimmedBody = body.replace(/\r\n\s*/g, "");
          expect(trimmedBody).to.equal(
            `<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Play>/cloudstore/file/mySoundId</Play></Response>`);
          next();
        })
        .end();
    });
  });
});
