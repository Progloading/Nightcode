(ns nightcode.process
  (:require [clojure.java.io :as io]
            [nightcode.spec :as spec]
            [nightcode.utils :as u]
            [clojure.spec :as s :refer [fdef]])
  (:import [com.hypirion.io ClosingPipe Pipe]))

(defn start-process!
  [process path args]
  (reset! process (.exec (Runtime/getRuntime)
                         (into-array args)
                         nil
                         (io/file path)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(when @process (.destroy @process))))
  (with-open [out (io/reader (.getInputStream @process))
              err (io/reader (.getErrorStream @process))
              in (io/writer (.getOutputStream @process))]
    (let [pump-out (doto (Pipe. out *out*) .start)
          pump-err (doto (Pipe. err *err*) .start)
          pump-in (doto (ClosingPipe. *in* in) .start)]
      (.join pump-out)
      (.join pump-err)
      (.waitFor @process)
      (reset! process nil))))

(defn start-java-process!
  [process path args]
  (let [java-cmd (or (System/getenv "JAVA_CMD") "java")
        jar-uri (u/get-exec-uri "nightcode.core")]
    (start-process! process path
      (flatten [java-cmd
                "-cp"
                (if (.isDirectory (io/file jar-uri))
                  (System/getProperty "java.class.path")
                  (u/uri->str jar-uri))
                args]))))

(defn stop-process!
  [process]
  (when-let [p @process]
    (doto p .destroy .waitFor))
  (reset! process nil))

; specs

(fdef start-process!
  :args (s/cat :process spec/atom? :path string? :args (s/coll-of string?)))

(fdef start-java-process!
  :args (s/cat :process spec/atom? :path string? :args (s/coll-of string?)))

(fdef stop-process!
  :args (s/cat :process spec/atom?))

