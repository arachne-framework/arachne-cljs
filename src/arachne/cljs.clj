(ns arachne.cljs
  "Utilities for working with CLJS in Arachne"
  (:require [clojure.spec :as s]
            [arachne.cljs.schema :as schema]
            [arachne.error :as e :refer [deferror error]]
            [arachne.core.dsl.specs :as core-specs]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as script :refer [defdsl]]
            [arachne.core.util :as u]))

(defn schema
  "Return the schema for the module"
  []
  schema/schema)

(defn configure
  "Configure the module"
  [cfg]
  cfg)