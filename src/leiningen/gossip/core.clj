(ns leiningen.gossip.core
  (:use 
    [leiningen.gossip.callgraphing :only [generate-dot-files-from-clj-files]]))

(defn gossip-about [src-dir tar-dir]
  (generate-dot-files-from-clj-files src-dir tar-dir))
