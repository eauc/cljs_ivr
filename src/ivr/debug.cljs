(ns ivr.debug)

(def debug? (not= "production"
                  (aget js/process "env" "NODE_ENV")))
