(ns arachne.cljs-test
  (:require [clojure.test :refer :all]
            [arachne.core :as arachne]
            [arachne.core.runtime :as rt]
            [arachne.core.dsl :as a]
            [arachne.cljs]
            [arachne.cljs.dsl :as cljs]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [arachne.core.config :as cfg]
            [com.stuartsierra.component :as component]
            [clojure.walk :as w]))

;; Used to smuggle a value into the config script
(def ^:dynamic *build-opts*)

(defn build-cfg
  "DSL function to build test config"
  []

  (cljs/options :test/options
    *build-opts*)

  (a/component :test/root {} 'clojure.core/hash-map)
  (a/runtime :test/rt [:test/root]))

(defn- normalize
  "Convert all nested sequences to sets so items can be compared in an order-agnostic way"
  [o]
  (w/prewalk (fn [f]
               (if (and (not (map-entry? f)) (sequential? f))
                 (into #{} f)
                 f)) o))

(defn- roundtrip
  [compile-opts]
  (binding [*build-opts* compile-opts]
    (let [cfg (arachne/build-config [:org.arachne-framework/arachne-cljs]
                '(arachne.cljs-test/build-cfg))]
      (arachne.cljs/cljs-config-map cfg [:arachne/id :test/options]))))


(defspec cljs-configs-roundtrip-through-arachne 70
  (prop/for-all [compile-opts (s/gen :arachne.cljs.dsl/compiler-options)]
    (let [output (roundtrip compile-opts)]
      (= (normalize output)
         (normalize compile-opts)))))