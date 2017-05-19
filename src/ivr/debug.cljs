(ns ivr.debug)

(def node-env (or (aget js/process "env" "NODE_ENV")
                  "development"))

(def debug? (not= "production" node-env))

(def test? (= "test" node-env))

(def default-log-level
  (if debug? (if test? "warn" "verbose") "info"))
