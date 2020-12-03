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
            [atomist.container :as container]
            [cljs-node-io.core :as io]
            [goog.string :as gstring]
            [atomist.proc :as proc]
            [atomist.cljs-log :as log]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn add-tag-to-request
  [handler]
  (fn [request]
    (go
      (if-let [{:git.ref/keys [name type]} (-> request :subscription :result first second)]
        (<! (handler (assoc request :atomist.main/tag name)))
        (<! (handler request))))))

(defn create-ref-from-event
  [handler]
  (fn [request]
    (go
      (let [{:git.commit/keys [repo sha]} (-> request :subscription :result first first)]
        (<! (handler (assoc request :ref {:repo (:git.repo/name repo)
                                          :owner (-> repo :git.repo/org :git.org/name)
                                          :sha sha}
                            :token (-> repo :git.repo/org :github.org/installation-token))))))))

(defn -js->clj+
  "For cases when built-in js->clj doesn't work. Source: https://stackoverflow.com/a/32583549/4839573"
  [x]
  (into {} (for [k (js-keys x)] [k (aget x k)])))

(defn add-deploy-profile
  [handler]
  (fn [request]
    (go
      (log/infof "Found resource providers: %s" (:atomist/resource-providers request))
      (log/infof "add-deploy profiles.clj profile for deploying %s to %s with user %s"
                 (:atomist.main/tag request)
                 "https://sforzando.jfrog.io/sforzando/libs-release-local"
                 (.. js/process -env -MVN_ARTIFACTORYMAVENREPOSITORY_USER))
      (io/spit
       (io/file (-> request :project :path) "profiles.clj")
       (pr-str
        {:lein-m2-deploy
         {:repositories [["releases" {:url "https://sforzando.jfrog.io/sforzando/libs-release-local"
                                      :username (.. js/process -env -MVN_ARTIFACTORYMAVENREPOSITORY_USER)
                                      :password (.. js/process -env -MVN_ARTIFACTORYMAVENREPOSITORY_PWD)
                                      :sign-releases false}]]}}))
      (<! (handler request)))))

(defn run-leiningen
  [handler lein-args-fn]
  (fn [request]
    (go
      (try
        (api/trace "run-leiningen-if-present")
        (let [f (io/file (-> request :project :path))
              env (-> (-js->clj+ (.. js/process -env))
                      (merge
                       {"_JAVA_OPTIONS" (str "-Duser.home=" (.getPath f))}))
              exec-opts {:cwd (.getPath f), :env env, :maxBuffer (* 1024 1024 5)}
              sub-process-port (proc/aexec (gstring/format "lein %s" (lein-args-fn request))
                                           exec-opts)
              [err stdout stderr] (<! sub-process-port)]
          (if err
            (do
              (log/error "process exited with code " (. err -code))
              (<! (handler
                   (assoc request
                          :atomist/summary (gstring/format "`lein deploy` error on %s/%s:%s" (-> request :ref :owner) (-> request :ref :repo) (-> request :ref :sha))
                          :checkrun/conclusion "failure"
                          :checkrun/output
                          {:title "Leiningen Deploy Failure"
                           :summary
                           (str
                            (apply str "stdout: \n" (take-last 150 stdout))
                            (apply str
                                   "\nstderr: \n"
                                   (take-last 150 stderr)))}))))
            (do
              (try
                (let [[org commit repo] (-> request :subscription :result first)
                      group-name (str (second (edn/read-string (io/slurp (io/file f "project.clj")))))
                      [group artifact-name] (str/split group-name #"/")
                     ;; for clojure where sometimes group is same as artifact
                      artifact-name (or artifact-name group)]
                  (<! (api/transact request [{:schema/entity-type :git/repo
                                              :schema/entity "$repo"
                                              :git.provider/url (:git.provider/url org)
                                              :git.repo/source-id (:git.repo/source-id repo)}
                                             {:schema/entity-type :git/commit
                                              :schema/entity "$commit"
                                              :git.provider/url (:git.provider/url org)
                                              :git.commit/sha (:git.commit/sha commit)
                                              :git.commit/repo "$repo"}
                                             {:schema/entity-type :maven/artifact
                                              :maven.artifact/commit "$commit"
                                              :maven.artifact/name artifact-name
                                              :maven.artifact/group group
                                              :maven.artifact/version (:tag request)}])))
                (catch :default ex
                  (log/error "Error transacting deployed artifact " ex)))

              (<! (handler
                   (assoc request
                          :atomist/summary (gstring/format
                                            "`lein deploy` success on %s/%s:%s"
                                            (-> request :ref :owner)
                                            (-> request :ref :repo)
                                            (-> request :ref :sha))
                          :checkrun/conclusion "success"
                          :checkrun/output
                          {:title "Leiningen Deploy Success"
                           :summary (apply str (take-last 300 stdout))}))))))
        (catch :default ex
          (log/error ex)
          (<! (api/finish
               (assoc request
                      :atomist/summary (gstring/format
                                        "`lein deploy` error on %s/%s:%s"
                                        (-> request :ref :owner)
                                        (-> request :ref :repo)
                                        (-> request :ref :sha))
                      :checkrun/conclusion "failure"
                      :checkrun/output
                      {:title "Lein Deploy error"
                       :summary "There was an error running lein deploy"})
               :failure
               "failed to run lein deploy")))))))

(defn ^:export handler
  [& args]
  ((-> (api/finished :success "handled event in lein m2 deploy skill")
       (run-leiningen (fn [request]
                        (gstring/format "change version set '\"%s\"' && lein with-profile lein-m2-deploy deploy" (:atomist.main/tag request))))
       (add-deploy-profile)
       (api/clone-ref)
       (api/with-github-check-run :name "lein-m2-deploy")
       (add-tag-to-request)
       (create-ref-from-event)
       (api/add-resource-providers)
       (api/add-skill-config)
       (api/log-event)
       (api/status :send-status (fn [{:atomist/keys [summary]}] summary))
       (container/mw-make-container-request))
   {}))