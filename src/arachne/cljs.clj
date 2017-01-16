(ns arachne.cljs
  "Utilities for working with CLJS in Arachne"
  (:require [clojure.spec :as s]
            [arachne.cljs.schema :as schema]
            [arachne.error :as e :refer [deferror error]]
            [arachne.core.config :as cfg]
            [arachne.core.util :as u]))

(defn schema
  "Return the schema for the module"
  []
  schema/schema)

(defn configure
  "Configure the module"
  [cfg]
  cfg)