var path = require("path");
try {
    require("source-map-support").install();
} catch(err) {
}
require(path.join(path.resolve("."),"build/app","goog","bootstrap","nodejs.js"));
require(path.join(path.resolve("."),"build/app","cljs_deps.js"));
goog.global.CLOSURE_UNCOMPILED_DEFINES = {"cljs.core._STAR_target_STAR_":"nodejs"};
goog.require("devtools.preload");
goog.require("ivr.server");
cljs.core._STAR_main_cli_fn_STAR_ = () => null;
goog.require("cljs.nodejscli");

console.log(ivr.server.start_test);
console.log(ivr.server.stop);

module.exports = {
  start: ivr.server.start_test,
  stop: ivr.server.stop,
};
