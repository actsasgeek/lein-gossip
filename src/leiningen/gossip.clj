(ns leiningen.gossip
  (:require [clojure.java.io :as io])
  (:use [leiningen.gossip.core :only [gossip-about]]))

(defn get-parameter-with-cascading-defaults [project args kvec i default]
  "Given the project data and the args passed into the task,
  look to see if the args contains something at the given index,
  if it does, return it. If it doesn't, look in the project for the
  key vector [:key1 ... :key2]. If that exists, return it,
  and if not return the default."
  (if (contains? args i)
    (args i)
    (get-in project kvec default)))

(defn extract-source-dir [project args]
  "Extract the source directory passed on the command line or the 
  source-paths in the project.clj file or the default (\"src\")."
  (get-parameter-with-cascading-defaults project args [:source-paths] 0 "src"))

(defn extract-target-dir [project args]
  "Extract the target directory passed on the command line or the
  value in [:gossip :target] in the project.clj or the default (\"doc/dot\")"
  (get-parameter-with-cascading-defaults project args [:gossip :target] 1 "doc/dot"))

(defn confirm-dir-exists [dirname]
  "Return true if the dirname exists and is a directory."
  (let [dir (io/file dirname)]
    (and (.exists dir) (.isDirectory dir))))

(defn create-dir! [dirname]
  "If the dirname does not exist, create it."
  (let [dir (io/file dirname)]
    (if (not (and (.exists dir) (.isDirectory dir)))
      (.mkdirs dir))))

(defn gossip [project & args]
  "The main event...

  Gossip will attempt to contruct a primitive call graph of the Clojure code
  in src (default), :source-paths (in project.clj) or the supplied value on the command line,
  first parameter.

  It will write the *.dot files to the doc/dot directory and make it if it does not
  exist or it will use [:gossip :target] in project.clj or the supplied value on the
  command line (second parameter)."
  (let [
   args-vec (vec args) ;; args is a list and not positional
   raw-args (extract-source-dir project args-vec)
   srcs (if (seq? raw-args) (vec raw-args) [raw-args])
  _ (println (str "Looking for Clojure files in " srcs))
   tar (extract-target-dir project args-vec)]
    (create-dir! tar)
    (doseq [src srcs]
      (if (confirm-dir-exists src)
        (gossip-about src tar)))))
