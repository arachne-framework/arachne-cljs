(ns arachne.cljs-test
  (:require [clojure.test :refer :all]
            [arachne.core :as arachne]
            [arachne.core.runtime :as rt]
            [arachne.core.dsl :as ac]
            [arachne.cljs.build :as build]
            [arachne.cljs.dsl :as cljs]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [arachne.core.config :as cfg]
            [arachne.assets.dsl :as a]
            [com.stuartsierra.component :as component]
            [clojure.walk :as w]
            [arachne.fileset :as fs]
            [clojure.java.io :as io]))

;; Used to smuggle a value into the config script
(def ^:dynamic *compiler-opts*)

(defn roundtrip-cfg
  "DSL function to build test config that doesn't do much with the config data."
  []
  (cljs/build :test/build
    :compiler-options *compiler-opts*)

  (ac/runtime :test/rt [:test/build]))

(defn- normalize
  "Convert all nested sequences to sets so items can be compared in an order-agnostic way"
  [o]
  (w/prewalk (fn [f]
               (if (and (not (map-entry? f)) (sequential? f))
                 (into #{} f)
                 f)) o))

(defn- roundtrip
  [compile-opts]
  (binding [*compiler-opts* compile-opts]
    (let [cfg (arachne/build-config [:org.arachne-framework/arachne-cljs]
                '(arachne.cljs-test/roundtrip-cfg))
          opts (cfg/q cfg '[:find ?co .
                            :where
                            [?b :arachne/id :test/build]
                            [?b :arachne.cljs.build/compiler-options ?co]])]
      (@#'build/extract (cfg/pull cfg '[*] opts)))))


(defspec cljs-configs-roundtrip-through-arachne 70
  (prop/for-all [compile-opts (s/gen :arachne.cljs.dsl/compiler-options)]
    (let [output (roundtrip compile-opts)]
      (= (normalize output)
         (normalize compile-opts)))))

(defn build-cfg
  "DSL function to build a simple CLJS config"
  [output-dir watch]

  ;; for all the ClojureScript compiler options, all paths are relative to the output fileset
  (cljs/build :test/builder
    :compiler-options {:output-to "js/main.js"
                       :output-dir "js"
                       :asset-path "js"
                       :optimizations :none
                       :main 'arachne.cljs.example})

  (a/input-dir :test/input "test" :watch? watch)
  (a/transform :test/build :test/input :test/builder)
  (a/output-dir :test/output :test/build output-dir)
  (ac/runtime :test/rt [:test/output]))

(deftest basic-build
  (let [output-dir (fs/tmpdir!)
        cfg (arachne/build-config [:org.arachne-framework/arachne-cljs]
              `(arachne.cljs-test/build-cfg ~(.getCanonicalPath output-dir) false))
        rt (component/start (rt/init cfg [:arachne/id :test/rt]))
        result (slurp (io/file output-dir "js/arachne/cljs/example.js"))]
    (is (re-find #"Hello world!" result))))


(comment

  (def cfg (arachne/build-config [:org.arachne-framework/arachne-cljs]
             '(arachne.cljs-test/build-cfg)))

  (def rt (rt/init cfg [:arachne/id :test/rt]))

  (def rt (component/start rt))
  (def rt (component/stop rt))


  )

