
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
            [atomist.gitflows :as gitflows]
            [cljs.core.async :as async :refer [<! >! chan timeout] :refer-macros [go]]
            [atomist.container :as container]
            [cljs-node-io.core :as io]
            [goog.string :as gstring]
            [atomist.proc :as proc]
            [atomist.cljs-log :as log]
            [atomist.git :as git]
            [clojure.string :as s]))

(defn create-ref-from-event
  [handler]
  (fn [request]
    (let [[org commit repo] (-> request :subscription :result first)]
      (handler (assoc request :ref {:repo (:git.repo/name repo)
                                    :owner (:git.org/name org)
                                    :sha (:git.commit/sha commit)}
                              :token (:github.org/installation-token org))))))

(defn -js->clj+
  "For cases when built-in js->clj doesn't work. Source: https://stackoverflow.com/a/32583549/4839573"
  [x]
  (into {} (for [k (js-keys x)] [k (aget x k)])))

(defn run-leiningen-if-present
  [handler lein-args-fn]
  (fn [request]
    (go
     (try
       (api/trace "run-leiningen-if-present")
       (let [atm-home (.. js/process -env -ATOMIST_HOME)
             f (io/file (-> request :project :path))
             env (-> (-js->clj+ (.. js/process -env))
                     (merge
                      {"MVN_ARTIFACTORYMAVENREPOSITORY_USER"
                       (.. js/process -env -MVN_ARTIFACTORYMAVENREPOSITORY_USER)
                       "MVN_ARTIFACTORYMAVENREPOSITORY_PWD"
                       (.. js/process -env -MVN_ARTIFACTORYMAVENREPOSITORY_PWD)
                       ;; use atm-home for .m2 directory
                       "_JAVA_OPTIONS" (str "-Duser.home=" atm-home)}))
             exec-opts
             {:cwd (.getPath f), :env env, :maxBuffer (* 1024 1024 5)}
             sub-process-port (proc/aexec (gstring/format "lein %s" (lein-args-fn request))
                                          exec-opts)
             [err stdout stderr] (<! sub-process-port)]
         (if err
           (do
             (log/error "process exited with code " (. err -code))
             (<! (handler
                  (assoc request
                    :checkrun/conclusion "failure"
                    :checkrun/output
                    {:title "Leiningen Deploy Failure"
                     :summary
                     (str
                      (apply str "stdout: \n" (take-last 150 stdout))
                      (apply str
                             "\nstderr: \n"
                             (take-last 150 stderr)))}))))
           (<! (handler
                (assoc request
                  :checkrun/conclusion "success"
                  :checkrun/output
                  {:title "Leiningen Deploy Success"
                   :summary (apply str (take-last 300 stdout))})))))
       (catch :default ex
         (log/error ex)
         (<! (api/finish
              (assoc request
                :checkrun/conclusion "failure"
                :checkrun/output
                {:title "Lein Deploy error"
                 :summary "There was an error running lein deploy"})
              :failure
              "failed to run lein deploy")))))))

(defn with-tag
  [handler]
  (fn [request]
    ;; report Check failures
    (go
     (let [context (merge
                    (:ref request)
                    {:token (:token request)}
                    {:path (-> request :project :path)})]
       (log/infof "with-tag starting on ref %s at path %s" (:ref request) (:path context))
       (let [{:keys [response]}
             (<! (atomist.gitflows/no-errors
                  (go context)
                  [[:async-git git/fetch-tags]
                   [:async-git git/git-rev-list]
                   [:async-git git/git-describe-tags]
                   [:sync #(assoc % :tag (gitflows/next-version (s/trim (:stdout %))))]
                   [:async (fn [{:keys [tag] :as context}]
                             (go
                              (let [response (<! (handler (assoc request :tag tag)))]
                                (merge
                                 context
                                 {:response response}
                                 (if (= "failure" (:checkrun/conclusion response))
                                   {:error true})))))]
                   [:async-git git/tag]
                   [:async-git git/push-tag]]))]
         response)))))

(defn ^:export handler
  [& args]
  ((-> (api/finished :success "handled event in lein m2 deploy skill")
       (api/status)
       (run-leiningen-if-present (fn [request]
                                   (gstring/format "change version set '\"%s\"' && lein deploy" (:tag request))))
       (with-tag)
       (api/clone-ref)
       (api/with-github-check-run :name "lein-m2-deploy")
       (create-ref-from-event)
       ;;(api/log-event)
       (container/mw-make-container-request))
   {}))