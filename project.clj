(defproject ivr "0.1.0-SNAPSHOT"
  :min-lein-version "2.0.0"
  :clean-targets ^{:protect false} ["build" "target"]
  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.9.521"]
   [org.clojure/core.async "0.3.443"]
   [org.clojure/core.match "0.3.0-alpha4"]
   [org.clojure/test.check "0.9.0"]
   [com.cognitect/transit-cljs "0.8.239"]
   [hiccups "0.3.0"]
   [re-frame "0.9.3"]
   [reagent "0.6.1"]]
  :hooks [leiningen.cljsbuild]
  :plugins
  [[lein-cljsbuild "1.1.5"]
   [lein-figwheel "0.5.10" :exclusions [org.clojure/clojure]]
   [lein-npm "0.6.2" :exclusions [org.clojure/clojure]]
   [lein-pprint "1.1.2"]]
  :source-paths ["src"]
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; Tools
  :cljsbuild
  {:builds
   {:app {:source-paths ["src"]
          :compiler {:main "ivr.core"
                     :optimizations :none
                     :output-to "build/app.js"
                     :output-dir "build/app"
                     :pretty-print true
                     :target :nodejs}}}}
  :figwheel
  {}
  :npm
  {:dependencies [[body-parser "^1.17.2"]
                  [compression "^1.6.2"]
                  [express "^4.15.2"]
                  [express-winston "^2.4.0"]
                  [helmet "^3.6.0"]
                  [superagent "^3.5.2"]
                  [xml-escape "^1.1.0"]
                  [winston "^2.3.1"]
                  [zeromq "^4.3.0"]]
   :root "build"}
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; Profiles
  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.9.4"]
                   [com.cemerick/piggieback "0.2.1"]
                   [figwheel-sidecar "0.5.10"]
                   [pjstadig/humane-test-output "0.8.1"]]
    :cljsbuild
    {:builds
     {:app {:figwheel {:on-jsload "ivr.server/create-app"}
            :compiler {:preloads [devtools.preload]}}
      :test {:figwheel {:on-jsload "ivr.core-test/run-tests"}
             :source-paths ["src" "test"]
             :compiler {:main "ivr.core-test"
                        :optimizations :none
                        :output-to "build/test.js"
                        :output-dir "build/test"
                        :preloads [devtools.preload]
                        :pretty-print true
                        :target :nodejs}}}}
    :npm
    {:dependencies [[nock "^9.0.13"]
                    [source-map-support "^0.4.15"]
                    [ws "^2.3.1"]]}}
   :production
   {}
   :repl [:dev]})
