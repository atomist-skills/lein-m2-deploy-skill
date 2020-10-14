
;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.pprint :refer [pprint]]
            [goog.string.format]
            [cljs.core.async :as async :refer [<! >! chan timeout] :refer-macros [go]]
            [atomist.container :as container]))

(defn ^:export handler
  [& args]
  ((-> (api/finished :success "handled event in lein m2 deploy skill")
       (api/status)
       (api/log-event)
       (container/mw-make-container-request))
   {}))