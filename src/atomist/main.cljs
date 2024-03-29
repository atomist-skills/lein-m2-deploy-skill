;; Copyright © 2021 Atomist, Inc.
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
            [goog.string.format]
            [atomist.async :refer-macros [<? go-safe]]
            [atomist.container :as container]
            [cljs-node-io.core :as io]
            [goog.string :as gstring]
            [atomist.proc :as proc]
            [atomist.cljs-log :as log]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cljs.reader]))

(defn add-tag-to-request
  [handler]
  (fn [request]
    (go-safe
      (if-let [tag (let [{:git.ref/keys [_commit]} (-> request :subscription :result first first)]
                     (->> _commit
                          (filter #(= :git.ref.type/tag (-> % :git.ref/type :db/ident keyword)))
                          last
                          :git.ref/name))]
        (<? (handler (assoc request :atomist.main/tag tag)))
        (do
          (log/infof "unable to extract tag from %s" (-> request :subscription :result first first))
          (assoc request :atomist/status {:code 1 :reason (gstring/format "no tag ref in results")}))))))

(defn create-ref-from-event
  [handler]
  (fn [request]
    (go-safe
      (let [{:git.commit/keys [repo sha]} (-> request :subscription :result first first)]
        (<? (handler (assoc request :ref {:repo (:git.repo/name repo)
                                          :owner (-> repo :git.repo/org :git.org/name)
                                          :sha sha}
                            :token (-> repo :git.repo/org :github.org/installation-token))))))))

(defn check-description-and-license
  [handler]
  (fn [request]
    (go-safe
      (cond
        (and
         (:description (:atomist.leiningen/non-evaled-project-map request))
         (:license (:atomist.leiningen/non-evaled-project-map request)))
        (<? (handler request))
        :else
        (assoc request
               :atomist/status
               {:code 1
                :reason (gstring/format "project.clj in the root of %s/%s missing either :description or :license"
                                        (-> request :ref :owner)
                                        (-> request :ref :repo))}
               :checkrun/conclusion "failure"
               :checkrun/output
               {:title "project.clj missing description or license"
                :summary "atomist/lein-m2-deploy skill requires that the project.clj define a `:description` and a `license`"})))))

(defn warn-about-deploy-branches
  [handler]
  (fn [request]
    (go-safe
      (when (-> request :atomist.leiningen/non-evaled-project-map :deploy-branches)
        (log/warn "WARNING:  this project contains a :deploy-branches key.  We recommend standardizing branch filters outside of Leiningen."))
      (<? (handler request)))))

(defn check-leiningen-project
  [handler]
  (fn [request]
    (go-safe
      (try
        (let [f (io/file (-> request :project :path) "project.clj")
              p (and (.exists f) (-> f (io/slurp) (cljs.reader/read-string)))
              p-map (->> p
                         (drop 3)
                         (partition 2)
                         (reduce #(apply assoc %1 %2) {}))]
          (<? (handler (assoc request :atomist.leiningen/non-evaled-project-map p-map))))
        (catch :default ex
          (assoc request
                 :atomist/status
                 {:code 1
                  :reason "invalid project.clj"}
                 :checkrun/conclusion "failure"
                 :checkrun/output
                 {:title "invalid project.clj"
                  :summary (str "atomist/lein-m2-deploy skill failed to read project.clj:  " ex)}))))))

(defn add-deploy-profile
  [handler]
  (fn [request]
    (go-safe
      (let [repo-map (reduce
                      (fn [acc [_ repo usage]]
                        (if (and repo usage)
                          (update acc (keyword usage) (fn [repos]
                                                        (conj (or repos []) repo)))
                          acc))
                      {}
                      (-> request :subscription :result))
            release-repo (-> repo-map :releases first)
            url (:maven.repository/url release-repo)
            username (:maven.repository/username release-repo)
            repo-id (:maven.repository/repository-id release-repo)
            password (:maven.repository/secret release-repo)
            sign? (boolean (:atomist/sign-releases? request))
            releases [repo-id {:url url
                               :username username
                               :password password
                               :sign-releases sign?}]]
        (log/infof "Signing: " sign?)
        (log/infof "Found releases integration: %s" (gstring/format "%s - %s" repo-id url))
        (log/infof "Found resolve integration: %s"
                   (->> (:resolve repo-map)
                        (map #(gstring/format "%s - %s" (:maven.repository/repository-id %) (:maven.repository/url %)))
                        (str/join ", ")))

        (log/infof "add-deploy profiles.clj profile for deploying %s to %s with user %s and password %s"
                   (:atomist.main/tag request)
                   url
                   username
                   (apply str (take (count password) (repeat 'X))))
        (io/spit
         (io/file (-> request :project :path) "profiles.clj")
         (pr-str
          {:lein-m2-deploy
           (merge
            {:deploy-repositories [releases]
             :repositories (->> (:resolve repo-map)
                                (map (fn [{:maven.repository/keys [repository-id url username secret]}]
                                       (log/infof "add-resolve profiles.clj profile for deploying %s to %s with user %s and password %s"
                                                  (:atomist.main/tag request)
                                                  url
                                                  username
                                                  (apply str (take (count secret) (repeat 'X))))
                                       [repository-id {:url url
                                                       :username username
                                                       :password secret}]))
                                (into []))}
           ;; if the root project does not specify a url then add one to the profile
            (when-not (-> request :atomist.leiningen/non-evaled-project-map :url)
              {:url (gstring/format "https://github.com/%s/%s" (-> request :ref :owner) (-> request :ref :repo))}))}))
        (<? (handler (assoc request :atomist/deploy-repo-id repo-id :atomist/deploy-repo-url url)))))))

(defn prepare-keys
  [handler]
  (fn [request]
    (go-safe
     (let [gpg-key (:gpg-private-key request)
           gpg-key-passphrase (:gpg-private-key-passphrase request)]
       (if gpg-key
         (do
           (log/infof "Found GPG private key. Importing for signing...")
           (let [gpg-key-file-name "/tmp/gpg.key"
                 pwd-file-name "/tmp/pwd.txt"
                 pwd-file (io/file pwd-file-name)
                 gpg-file (io/file gpg-key-file-name)]
             (io/spit gpg-file gpg-key)
             (when gpg-key-passphrase
               (log/infof "Importing with passphrase")
               (io/spit pwd-file gpg-key-passphrase))
             (try
               (<? (proc/aexec "gpg-agent --daemon" {:maxBuffer (* 1024 1024 5)}))
               (log/info "gpg agent started")
               (let [sub-process-port (proc/aexec (if gpg-key-passphrase
                                                    (gstring/format "gpg --pinentry-mode loopback --passphrase-file=%s --import %s" pwd-file-name gpg-key-file-name)
                                                    (gstring/format "gpg --import %s" gpg-key-file-name)) {:maxBuffer (* 1024 1024 5)})
                     [err stdout stderr] (<? sub-process-port)]
                 (if err
                   (do
                     (log/error "gpg process exited with code " (. err -code))
                     (assoc request
                            :atomist/status {:code 1
                                             :reason
                                             (gstring/format
                                              "`gpg key import` error on %s/%s:%s"
                                              (-> request :ref :owner)
                                              (-> request :ref :repo)
                                              (-> request :ref :sha))}
                            :checkrun/conclusion "failure"
                            :checkrun/output
                            {:title "Lein Deploy error"
                             :summary "There was an error configuring gpg"}))
                   (do
                     (log/info "gpg keys imported successfully")
                     (<? (handler (assoc request :atomist/sign-releases? true))))))
               (catch :default ex
                 (log/error ex)
                 (assoc request
                        :atomist/status {:code 1
                                         :reason
                                         (gstring/format
                                          "`gpg key import` error on %s/%s:%s"
                                          (-> request :ref :owner)
                                          (-> request :ref :repo)
                                          (-> request :ref :sha))}
                        :checkrun/conclusion "failure"
                        :checkrun/output
                        {:title "Lein Deploy error"
                         :summary "There was an error configuring gpg"})))))
         (do
           (log/infof "No GPG keys found, not signing")
           (<? (handler request))))))))

(defn -js->clj+
  "For cases when built-in js->clj doesn't work. Source: https://stackoverflow.com/a/32583549/4839573"
  [x]
  (into {} (for [k (js-keys x)] [k (aget x k)])))

(defn run-leiningen
  [handler lein-args-fn]
  (fn [request]
    (go-safe
      (try
        (api/trace "run-leiningen-if-present")
        (let [f (io/file (-> request :project :path))
              env (-> (-js->clj+ (.. js/process -env))
                      (merge
                       {"LEIN_GPG" "/usr/bin/gpg.sh"
                        "_JAVA_OPTIONS" (str "-Duser.home=" (.getPath f))}))
              exec-opts {:cwd (.getPath f), :env env, :maxBuffer (* 1024 1024 5)}
              sub-process-port (proc/aexec (gstring/format "lein %s" (lein-args-fn request))
                                           exec-opts)
              [err stdout stderr] (<? sub-process-port)]
          (if err
            (do
              (log/error "process exited with code " (. err -code))
              (<? (handler
                   (assoc request
                          :atomist/status
                          {:code (. err -code)
                           :reason (gstring/format "`lein deploy` error on %s/%s:%s"
                                                   (-> request :ref :owner)
                                                   (-> request :ref :repo)
                                                   (:atomist.main/tag request))}
                          :checkrun/conclusion "failure"
                          :checkrun/output
                          {:title "Leiningen Deploy Failure"
                           :summary
                           (str
                            (apply str "stdout: \n" (take-last 150 stdout))
                            (apply str
                                   "\nstderr: \n"
                                   (take-last 150 stderr)))}))))
            (try
              (let [[commit repo] (-> request :subscription :result first)
                    org (:git.repo/org repo)
                    group-name (str (second (edn/read-string (io/slurp (io/file f "project.clj")))))
                    [group artifact-name] (str/split group-name #"/")
                     ;; for clojure where sometimes group is same as artifact
                    artifact-name (or artifact-name group)]
                (<? (api/transact request [{:schema/entity-type :git/repo
                                            :schema/entity "$repo"
                                            :git.provider/url (:git.provider/url repo)
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
                                            :maven.artifact/version (:tag request)}]))
                (<? (handler
                     (assoc request
                            :atomist/status
                            {:code 0
                             :reason
                             (gstring/format
                              "Deployed _%s:%s_ to %s"
                              artifact-name
                              (:atomist.main/tag request)
                              (:atomist/deploy-repo-url request))}
                            :checkrun/conclusion "success"
                            :checkrun/output
                            {:title "Leiningen Deploy Success"
                             :summary (apply str (take-last 300 stdout))}))))
              (catch :default ex
                (log/error "Error transacting deployed artifact " ex)))))
        (catch :default ex
          (log/error ex)
          (assoc request
                 :atomist/status {:code 1
                                  :reason
                                  (gstring/format
                                   "`lein deploy` error on %s/%s:%s"
                                   (-> request :ref :owner)
                                   (-> request :ref :repo)
                                   (-> request :ref :sha))}
                 :checkrun/conclusion "failure"
                 :checkrun/output
                 {:title "Lein Deploy error"
                  :summary "There was an error running lein deploy"}))))))

(defn ^:export handler
  [& args]
  ((-> (api/finished)
       (run-leiningen (fn [request]
                        (gstring/format
                         "change version set '\"%s\"' && lein with-profile lein-m2-deploy deploy %s"
                         (:atomist.main/tag request)
                         (:atomist/deploy-repo-id request))))
       (add-deploy-profile)
       (prepare-keys)
       (check-description-and-license)
       (warn-about-deploy-branches)
       (check-leiningen-project)
       (api/clone-ref)
       (api/with-github-check-run :name "lein-m2-deploy")
       (add-tag-to-request)
       (create-ref-from-event)
       (api/add-skill-config)
       ;(api/log-event)
       (api/status)
       (container/mw-make-container-request))
   {}))
