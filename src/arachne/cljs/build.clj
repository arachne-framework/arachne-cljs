(ns arachne.cljs.build
  (:require
    [cljs.build.api :as cljs]
    [clojure.spec :as s]
    [arachne.cljs.schema :as schema]
    [arachne.error :as e :refer [deferror error]]
    [arachne.core.dsl.specs :as core-specs]
    [arachne.core.config :as cfg]
    [arachne.core.config.init :as script :refer [defdsl]]
    [arachne.core.util :as u]
    [arachne.assets.pipeline :as p]
    [arachne.fileset :as fs]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]))

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

(defn- extract
  "Giventhe entity map of a arachne.cljs/CompilerOptions entity, return a standard CLJS options
   map, as it was stored in the config."
  [entity]
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
    :arachne.cljs.compiler-options/output-wrapper :output-wrapper identity
    :arachne.cljs.compiler-options/libs :libs vec
    :arachne.cljs.compiler-options/preamble :preamble vec
    :arachne.cljs.compiler-options/hashbang :hashbang identity
    :arachne.cljs.compiler-options/compiler-stats :compiler-stats identity
    :arachne.cljs.compiler-options/language-in :language-in identity
    :arachne.cljs.compiler-options/language-out :language-out identity
    :arachne.cljs.compiler-options/closure-extra-annotations :closure-extra-annotations set
    :arachne.cljs.compiler-options/anon-fn-naming-policy :anon-fn-naming-policy identity
    :arachne.cljs.compiler-options/optimize-constants :optimize-constants identity))

;(fs/checksum fs false)

(defn- append-path
  "Apped the given path suffix to a base path (a File)."
  [base suffix]
  (let [suffix (or suffix "")]
    (.getCanonicalPath (io/file base suffix))))

(defn update-if-present
  "Like clojure.core/update, but does not alter the map if the key is not present"
  [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(defn- compiler-options
  "Return a map of ClojureScript compiler options, ready to use.

  Takes the config, the eid of the CompilerOptions entity, ad a File of the absolute output directory"
  [options-entity out-dir]
  (-> (extract options-entity)
    (update-if-present :output-to #(append-path out-dir %))
    (update-if-present :output-dir #(append-path out-dir %))
    (update-if-present :source-map #(if (string? %)
                           (append-path out-dir %)
                           %))
    (update-if-present :externs #(map (fn [extern] (append-path out-dir extern)) %))
    (update-if-present :libs #(map (fn [lib] (append-path out-dir lib)) %))
    (update-if-present :preamble #(map (fn [pre] (append-path out-dir pre)) %))
    (update-if-present :modules (fn [module-map]
                                  (let [module-map (into {} (map (fn [[name module-opts]]
                                                                   [name (update module-opts :output-to
                                                                           #(append-path out-dir %))])
                                                              module-map))]
                                    (if (empty? module-map)
                                      nil
                                      module-map))))))

(defrecord Transformer [options-entity out-dir]
  p/Transformer
  (-transform [this input-fs]
    (let [src-dir (fs/tmpdir!)]
      (fs/commit! input-fs src-dir)
      (log/info "Building CLJS...")
      (cljs/build (.getCanonicalPath src-dir)
        (compiler-options options-entity out-dir))
      (log/info "CLJS build complete")
      (fs/add (fs/empty input-fs) out-dir))))

(defn build-transformer
  "Constructor function for transformer component for a CLJS build"
  [entity]
  (->Transformer (:arachne.cljs.build/compiler-options entity) (fs/tmpdir!)))