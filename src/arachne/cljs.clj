(ns arachne.cljs
  "Utilities for working with CLJS in Arachne"
  (:require [clojure.spec :as s]
            [arachne.cljs.schema :as schema]
            [arachne.error :as e :refer [deferror error]]
            [arachne.core.dsl.specs :as core-specs]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as script :refer [defdsl]]
            [arachne.core.util :as u]))

(defn- foreign-lib
  [entity]
  (u/map-transform entity {}
    :arachne.cljs.foreign-library/file :file identity
    :arachne.cljs.foreign-library/file-min :file-min identity
    :arachne.cljs.foreign-library/provides :provides vec
    :arachne.cljs.foreign-library/requires :requires vec
    :arachne.cljs.foreign-library/module-type :module-type identity
    :arachne.cljs.foreign-library/preproccess :preprocess identity))

(defn- modules
  [modules]
  (reduce (fn [output module]
            (assoc output (:arachne.cljs.closure-module/id module)
              (u/map-transform module {}
                :arachne.cljs.closure-module/output-to :output-to identity
                :arachne.cljs.closure-module/entries :entries set
                :arachne.cljs.closure-module/depends-on :depends-on set)))
    {} modules))

(defn- warnings
  [warnings]
  (into {} (map (fn [warning]
                  [(:arachne.cljs.warning/type warning)
                   (:arachne.cljs.warning/enabled warning)]) warnings)))

(defn- closure-warnings
  [warnings]
  (into {} (map (fn [warning]
                  [(:arachne.cljs.closure-warning/type warning)
                   (:arachne.cljs.closure-warning/value warning)]) warnings)))

(defn- closure-defines
  [defines]
  (into {} (map (fn [define]
                  [(:arachne.cljs.closure-define/variable define)
                   (:arachne.cljs.closure-define/annotate define)]) defines)))

(defn cljs-config-map
  "Given a config and the EID of an arachne.cljs/CompilerOptions entity, return a standard CLJS
   options map, suitable for passing to the CLJS compiler"
  [cfg eid]
  (let [entity (cfg/pull cfg '[*] eid)]
    (u/map-transform entity {}
      :arachne.cljs.compiler-options/main :main #(symbol (namespace %) (name %))
      :arachne.cljs.compiler-options/asset-path :asset-path identity
      :arachne.cljs.compiler-options/output-to :output-to identity
      :arachne.cljs.compiler-options/output-dir :output-dir identity
      :arachne.cljs.compiler-options/foreign-libs :foreign-libs #(map foreign-lib %)
      :arachne.cljs.compiler-options/modules :modules modules
      :arachne.cljs.compiler-options/common-warnings? :warnings identity
      :arachne.cljs.compiler-options/warnings :warnings warnings
      :arachne.cljs.compiler-options/closure-warnings :closure-warnings closure-warnings
      :arachne.cljs.compiler-options/closure-defines :closure-defines closure-defines
      :arachne.cljs.compiler-options/optimizations :optimizations identity
      :arachne.cljs.compiler-options/source-map :source-map #(cond
                                                               (= % "true") true
                                                               (= % "false") false
                                                               :else %)
      :arachne.cljs.compiler-options/verbose :verbose identity
      :arachne.cljs.compiler-options/pretty-print :pretty-print identity
      :arachne.cljs.compiler-options/target :target identity
      :arachne.cljs.compiler-options/externs :externs vec
      :arachne.cljs.compiler-options/preloads :preloads #(vec (map (fn [kw] (symbol (namespace kw) (name kw))) %))
      :arachne.cljs.compiler-options/source-map-path :source-map-path identity
      :arachne.cljs.compiler-options/source-map-asset-path :source-map-asset-path identity
      :arachne.cljs.compiler-options/source-map-timestamp :source-map-timestamp identity
      :arachne.cljs.compiler-options/cache-analysis :cache-analysis identity
      :arachne.cljs.compiler-options/recompile-dependents :recompile-dependents identity
      :arachne.cljs.compiler-options/static-fns :static-fns identity
      :arachne.cljs.compiler-options/load-tests :load-tests identity
      :arachne.cljs.compiler-options/elide-asserts :elide-asserts identity
      :arachne.cljs.compiler-options/pseudo-names :pseudo-names identity
      :arachne.cljs.compiler-options/print-input-delimiter :print-input-delimiter identity
      :arachne.cljs.compiler-options/output-wrapper :output-wrapper  identity
      :arachne.cljs.compiler-options/libs :libs vec
      :arachne.cljs.compiler-options/preamble :preamble vec
      :arachne.cljs.compiler-options/hashbang :hashbang identity
      :arachne.cljs.compiler-options/compiler-stats :compiler-stats identity
      :arachne.cljs.compiler-options/language-in :language-in identity
      :arachne.cljs.compiler-options/language-out :language-out identity
      :arachne.cljs.compiler-options/closure-extra-annotations :closure-extra-annotations set
      :arachne.cljs.compiler-options/anon-fn-naming-policy :anon-fn-naming-policy identity
      :arachne.cljs.compiler-options/optimize-constants :optimize-constants identity)))

(defn schema
  "Return the schema for the module"
  []
  schema/schema)

(defn configure
  "Configure the module"
  [cfg]
  cfg)