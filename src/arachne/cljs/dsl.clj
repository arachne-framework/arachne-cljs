(ns arachne.cljs.dsl
  "DSL code to handle ClojureScript compiler options"
  (:require [clojure.spec :as s]
            [arachne.error :as e :refer [deferror error]]
            [arachne.core.dsl.specs :as core-specs]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as script :refer [defdsl]]
            [arachne.core.util :as u]))

(s/def ::string (s/and string? #(not-empty %)))

(s/def ::file ::string)
(s/def ::provides (s/coll-of ::string :min-count 1))
(s/def ::file-min ::string)
(s/def ::requires (s/coll-of ::string :min-count 1))
(s/def ::module-type #{:commonjs :amd :es6})
(s/def ::preprocess keyword?)

(s/def ::foreign-libs
  (s/coll-of
    (s/keys
      :req-un [::file
               ::provides]
      :opt-un [::file-min
               ::requires
               ::module-type
               ::preprocess])
    :min-count 1))

(s/def ::entries (s/coll-of ::string :min-count 1 :into #{}))
(s/def ::depends-on (s/coll-of keyword? :min-count 1 :into #{}))

(s/def ::module
  (s/keys
    :req-un [::output-to
             ::entries]
    :opt-un [::depends-on]))

(s/def ::modules (s/map-of keyword? ::module :min-count 1))

(s/def ::warnings (s/or :boolean boolean?
                        :enumerated (s/map-of keyword? boolean? :min-count 1)))

(s/def ::closure-warnings (s/map-of keyword? #{:off :error :warning} :min-count 1))

(s/def ::closure-defines (s/map-of ::string boolean? :min-count 1))

(s/def ::optimizations #{:none :whitespace :simple :advanced})

(s/def ::source-map (s/or :boolean boolean?
                          :string ::string))

(s/def ::verbose boolean?)
(s/def ::pretty-print boolean?)
(s/def ::target #{:nodejs})
(s/def ::externs (s/coll-of ::string :min-count 1))
(s/def ::preloads (s/coll-of symbol? :min-count 1))

(s/def ::source-map-path ::string)
(s/def ::source-map-asset-path ::string)
(s/def ::source-map-timestamp boolean?)
(s/def ::cache-analysis boolean?)
(s/def ::recompile-dependents boolean?)
(s/def ::static-fns boolean?)
(s/def ::load-tests boolean?)
(s/def ::elide-asserts boolean?)
(s/def ::pseudo-names boolean?)
(s/def ::print-input-delimiter boolean?)
(s/def ::output-wrapper boolean?)
(s/def ::libs (s/coll-of ::string :min-count 1))
(s/def ::preamble (s/coll-of ::string :min-count 1))
(s/def ::hashbang boolean?)
(s/def ::compiler-stats boolean?)
(s/def ::language-in #{:ecmascript3, :ecmascript5, :ecmascript5-strict, :ecmascript6-typed, :ecmascript6-strict, :ecmascript6, :no-transpile})
(s/def ::language-out #{:ecmascript3, :ecmascript5, :ecmascript5-strict, :ecmascript6-typed, :ecmascript6-strict, :ecmascript6, :no-transpile})
(s/def ::closure-extra-annotations (s/coll-of ::string :min-count 1 :into #{}))
(s/def ::anon-fn-naming-policy #{:off :mapped :unmapped})
(s/def ::optimize-constants boolean?)
(s/def ::main symbol?)
(s/def ::output-to ::string)
(s/def ::output-dir ::string)
(s/def ::asset-path ::string)


;; Note: these are more strict than CLJS itself, but they avoid several confusing edge cases, and are almost
;; certainly what you want anyway.
(s/def ::compiler-options
  (s/keys
    :req-un [(or ::output-to ::modules)
             ::output-dir
             ::optimizations
             ::main]
    :opt-un [::asset-path
             ::foreign-libs
             ::warnings
             ::closure-warnings
             ::closure-defines
             ::source-map
             ::verbose
             ::pretty-print
             ::target
             ::externs
             ::preloads
             ::source-map-timestamp
             ::source-map-path
             ::source-map-asset-path
             ::cache-analysis
             ::recompile-dependents
             ::static-fns
             ::load-tests
             ::elide-asserts
             ::pseudo-names
             ::print-input-delimiter
             ::output-wrapper
             ::libs
             ::preamble
             ::hashbang
             ::compiler-stats
             ::language-in
             ::language-out
             ::closure-extra-annotations
             ::anon-fn-naming-policy
             ::optimize-constants]))

(defn- foreign-lib
  [m]
  (u/map-transform m {}
    :file :arachne.cljs.foreign-library/file identity
    :file-min :arachne.cljs.foreign-library/file-min identity
    :provides :arachne.cljs.foreign-library/provides vec
    :requires :arachne.cljs.foreign-library/requires vec
    :module-type :arachne.cljs.foreign-library/module-type identity
    :preprocess :arachne.cljs.foreign-library/preproccess identity))

(defn- modules
  [module-map]
  (vec (map (fn [[id mm]]
              (u/map-transform mm {:arachne.cljs.closure-module/id id}
                :output-to :arachne.cljs.closure-module/output-to identity
                :entries :arachne.cljs.closure-module/entries vec
                :depends-on :arachne.cljs.closure-module/depends-on vec))
         module-map)))

(defn- warnings
  [[tag warnings-map]]
  (when (= :enumerated tag)
    (vec (map (fn [[type enabled]]
                {:arachne.cljs.warning/type type
                 :arachne.cljs.warning/enabled enabled})
           warnings-map))))

(defn- closure-warnings
  [warnings-map]
  (vec (map (fn [[type value]]
              {:arachne.cljs.closure-warning/type type
               :arachne.cljs.closure-warning/value value})
         warnings-map)))

(defn- closure-defines
  [defines-map]
  (vec (map (fn [[variable annotate]]
              {:arachne.cljs.closure-define/variable (str variable)
               :arachne.cljs.closure-define/annotate annotate})
         defines-map)))

(defn- compiler-options
  "Given a conformed map of compiler options, return an entity map for a arachne.cljs/CompilerOptions entity."
  [opts]
  (u/map-transform opts {}
    :main :arachne.cljs.compiler-options/main keyword
    :asset-path :arachne.cljs.compiler-options/asset-path identity
    :output-to :arachne.cljs.compiler-options/output-to identity
    :output-dir :arachne.cljs.compiler-options/output-dir identity
    :foreign-libs :arachne.cljs.compiler-options/foreign-libs #(map foreign-lib %)
    :modules :arachne.cljs.compiler-options/modules modules
    :warnings :arachne.cljs.compiler-options/common-warnings? (fn [[tag val]] (when (= :boolean tag) val))
    :warnings :arachne.cljs.compiler-options/warnings warnings
    :closure-warnings :arachne.cljs.compiler-options/closure-warnings closure-warnings
    :closure-defines :arachne.cljs.compiler-options/closure-defines closure-defines
    :optimizations :arachne.cljs.compiler-options/optimizations identity
    :source-map :arachne.cljs.compiler-options/source-map (fn [[tag val]] (str val))
    :verbose :arachne.cljs.compiler-options/verbose identity
    :pretty-print :arachne.cljs.compiler-options/pretty-print identity
    :target :arachne.cljs.compiler-options/target identity
    :externs :arachne.cljs.compiler-options/externs vec
    :preloads :arachne.cljs.compiler-options/preloads #(vec (map keyword %))
    :source-map-path :arachne.cljs.compiler-options/source-map-path identity
    :source-map-asset-path :arachne.cljs.compiler-options/source-map-asset-path identity
    :source-map-timestamp :arachne.cljs.compiler-options/source-map-timestamp identity
    :cache-analysis :arachne.cljs.compiler-options/cache-analysis identity
    :recompile-dependents :arachne.cljs.compiler-options/recompile-dependents identity
    :static-fns :arachne.cljs.compiler-options/static-fns identity
    :load-tests :arachne.cljs.compiler-options/load-tests identity
    :elide-asserts :arachne.cljs.compiler-options/elide-asserts identity
    :pseudo-names :arachne.cljs.compiler-options/pseudo-names identity
    :print-input-delimiter :arachne.cljs.compiler-options/print-input-delimiter identity
    :output-wrapper :arachne.cljs.compiler-options/output-wrapper identity
    :libs :arachne.cljs.compiler-options/libs vec
    :preamble :arachne.cljs.compiler-options/preamble vec
    :hashbang :arachne.cljs.compiler-options/hashbang identity
    :compiler-stats :arachne.cljs.compiler-options/compiler-stats identity
    :language-in :arachne.cljs.compiler-options/language-in identity
    :language-out :arachne.cljs.compiler-options/language-out identity
    :closure-extra-annotations :arachne.cljs.compiler-options/closure-extra-annotations vec
    :anon-fn-naming-policy :arachne.cljs.compiler-options/anon-fn-naming-policy identity
    :optimize-constants :arachne.cljs.compiler-options/optimize-constants identity))

(s/def ::input ::core-specs/id)

(s/def ::build-options
  (s/keys* :req-un [::compiler-options]))

(s/fdef build
  :args (s/cat :arachne-id ::core-specs/id
               :build-options ::build-options))

(defn- input
  "Return the entity map for an input, given its Arachne ID"
  [aid]
  {:arachne/id aid})

(defdsl build
  "Define ClojureScript compiler options by defining a Asset Transformer that builds ClojureScript"
  [arachne-id & build-options]
  (let [conformed (s/conform ::build-options build-options)
        transformer (u/map-transform conformed {:arachne/id arachne-id
                                                :arachne.component/constructor :arachne.cljs.build/build-transformer}
                      :compiler-options :arachne.cljs.build/compiler-options compiler-options)]
    (script/transact [transformer])))
