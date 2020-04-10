(ns lint-action
  (:require [cheshire.core :as cheshire]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [clj-time.core :as clj-time]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]]))

(def check-name "my-clj-lint-action check")

(def eastwood-linters [:bad-arglists :constant-test :def-in-def :deprecations
                       :keyword-typos :local-shadows-var :misplaced-docstrings
                       :no-ns-form-found :non-clojure-file :redefd-vars
                       :suspicious-expression :suspicious-test :unlimited-use
                       :unused-fn-args :unused-locals :unused-meta-on-macro
                       :unused-namespaces :unused-private-vars :unused-ret-vals
                       :unused-ret-vals-in-try :wrong-arity :wrong-ns-form
                       :wrong-pre-post :wrong-tag])

(defn- make-header []
  {"Content-Type" "application/json"
   "Accept" "application/vnd.github.antiope-preview+json"
   "Authorization" (str "Bearer " (env :input-github-token))
   "User-Agent" "my-clj-lint-action"})

(defn start-action []
  (let [post-result (client/post (str "https://api.github.com/repos/"
                                      (env :github-repository)
                                      "/check-runs")
                                 {:headers (make-header)
                                  :content-type :json
                                  :body
                                  (cheshire/generate-string
                                   {:name check-name
                                    :head_sha (env :github-sha)
                                    :status "in_progress"
                                    :started_at (str (clj-time/now))})})]
    (-> (cheshire/parse-string (:body post-result))
        (get "id"))))

(defn update-action [id conclusion output]
  (client/patch
   (str "https://api.github.com/repos/"
        (env :github-repository)
        "/check-runs/"
        id)
   {:headers (make-header)
    :content-type :json
    :body
    (cheshire/generate-string
     {:name check-name
      :head_sha (env :github-sha)
      :status "completed"
      :completed_at (str (clj-time/now))
      :conclusion conclusion
      :output
      {:title check-name
       :summary "Results of linters."
       :annotations output}})}))

(defn run-clj-kondo [dir]
  (let [kondo-result (sh "/usr/local/bin/clj-kondo" "--lint" dir)
        result-lines
        (when (= (:exit kondo-result) 2)
          (str/split-lines (:out kondo-result)))]
    (->> result-lines
         (map (fn [line]
                (when-let [matches (re-matches #"^(.*?)\:(\d*?)\:(\d*?)\:([a-z ]*)\:(.*)" line)]
                  {:path (subs (second matches) (count dir))
                   :start_line (Integer. (nth matches 2))
                   :end_line (Integer. (nth matches 2))
                   :annotation_level "warning"
                   :message (str "[clj-kondo]" (nth matches 5))})))
         (filter identity))))

(defn get-files [dir]
  (let [files (sh "find" dir "-name" "*.clj")]
    (when (zero? (:exit files))
      (str/split-lines (:out files)))))

(defn run-cljfmt [files]
  (let [cljfmt-result (sh "sh"
                          "-c"
                          (str
                           "clojure -Sdeps \"{:deps {cljfmt {:mvn/version \\\"RELEASE\\\" }}}\" -m cljfmt.main check "
                           (str/join " " files)))]
    (when (not (zero? (:exit cljfmt-result)))
      (->> (:err cljfmt-result)
           str/split-lines
           (filter #(re-matches #"--- a(.*clj)$" %))
           (map (fn [line]
                  {:path (subs line (count "--- a"))
                   :start_line 0
                   :end_line 0
                   :annotation_level "warning"
                   :message "[cljfmt] cljfmt fail."}))))))

(defn run-eastwood [dir]
  (let [eastwood-result (sh
                         "sh" "-c"
                         (str
                          "cd " dir ";"
                          "clojure "
                          "-Sdeps " "\" {:deps {jonase/eastwood {:mvn/version \\\"RELEASE\\\" }}}\" "
                          " -m  " "eastwood.lint "
                          (pr-str (pr-str {:source-paths ["src"]
                                           :linters eastwood-linters}))))]
    (->> (str/split-lines (:out eastwood-result))
         (map (fn [line]
                (when-let [matches (re-matches #"^(.*?)\:(\d*?)\:(\d*?)\:(.*)\:(.*)" line)]
                  (let [message (str "[eastwood]"
                                     "[" (str/trim (nth matches 4)) "]"
                                     (nth matches 5))]
                    {:path (second matches)
                     :start_line (Integer. (nth matches 2))
                     :end_line (Integer. (nth matches 2))
                     :annotation_level "warning"
                     :message message}))))
         (filter identity))))

(defn run-kibit [dir]
  (let [eastwood-result (sh
                         "sh" "-c"
                         (str
                          "cd " dir ";"
                          "clojure "
                          "-Sdeps " "\" {:deps {tvaughan/kibit-runner {:mvn/version \\\"RELEASE\\\" }}}\" "
                          " -m  " "kibit-runner.cmdline "))]
    (->> (str/split (:out eastwood-result) #"\n\n")
         (map (fn [line]
                (let [message-lines (str/split-lines line)
                      first-line (first message-lines)
                      message (str/join "\n" (next message-lines))]
                  (when-let [line-decompose (re-matches #"At (.*?):(\d*?):$" first-line)]
                    {:path (second line-decompose)
                     :start_line (Integer. (nth line-decompose 2))
                     :annotation_level "warning"
                     :end_line (Integer. (nth line-decompose 2))
                     :message (str "[kibit]\n" message)}))))
         (filter identity))))

(def default-option {:linters "all" :cwd "./"})

(defn- fix-option [option]
  (->> option
       (map (fn [[k v]]
              (cond
                (= [k v] [:linters "all"]) [:linters ["eastwood" "cljfmt" "kibit" "clj-kondo"]]
                :else [k v])))
       (into {})))

(defn- run-linters [linters dir]
  (when-not (coll? linters) (throw (ex-info "Invalid linters." {})))
  (->> linters
       (map #(case %
               "eastwood" (run-eastwood dir)
               "kibit" (run-kibit dir)
               "cljfmt" (run-cljfmt (get-files dir))
               "clj-kondo" (run-clj-kondo dir)))
       (apply concat)))

(defn -main
  ([] (-main (pr-str default-option)))
  ([arg-string]
   (let [id (start-action)
         option (->> (clojure.edn/read-string arg-string)
                     (merge default-option)
                     fix-option)
         lint-result (run-linters (:linters option)
                                  (:cwd option))]
     (update-action id
                    (if (empty? lint-result) "success" "neutral")
                    lint-result))))
