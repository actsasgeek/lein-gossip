(ns leiningen.gossip.callgraphing
  (:use 
    [clojure.set :only [map-invert]]
    [clojure.string :only [join]]
    [clojure.java.io :only [file]]))

(defn clj-to-data [file]
"
Specify the full path of the .clj file and it will slurp it up, wrap it
in parentheses and apply read-string to it.
"
  (read-string (str "(" (slurp file) ")")))

(defn select-ns [code]
"
code is the .clj file that has been passed through the reader. This
function returns the (ns ...) list.
"
  (first (filter #(= 'ns (first %)) code)))

(defn select-defs [code]
"
code is a .clj file that has been passed through the reader. This
function returns all of the (defn ...) lists. Right now it only finds
defns.
"
  (filter #(contains? #{'defn 'def 'defn- 'defmulti 'defmethod} (first %)) code))


(defn extract-def-names [defs]
"
With a list of defn lists, it returns what the defs name.
"
  (map #(nth % 1) defs))

;; (:use [namespace] or [namespace :only [f1 f2]])

(defn process-required-namespace [namespace]
"
Under the assumption that all namespaces specified in the :require section
of the ns declaration are of the form [namespace] or [namespace :as abbreviation].

Because any function in that namespace will appear as namespace/f or abbreviation/f
in the code, we want to create a mapping between abbrevations and namespaces. This function
assists with this by returning [namespace namespace] or [abbreviation namespace].
"
  (if (= 3 (count namespace))
    [(namespace 2) (namespace 0)]
    [(namespace 0) (namespace 0)]))

(defn create-required-namespace-lookup [namespace]
"
Given a ns declaration, find the :require section and extract a mapping
for every namespace that is used so that functions from that namespace
can be identified by the full namespace name.

Return a map {abbrev1 ns1, abbrev2 ns2, ... }
"
  (let [
    require-expression (first (filter #(and (seq? %) (= :require (first %))) namespace))
    requires (rest require-expression)]
      (into {} (map process-required-namespace requires))))


(defn create-used-namespace-lookup [namespace]
"
Given a ns declaration, we extract the :use section and return a map
of functions that have been used and the namespace they are used
from. This assumes that all used namespaces use the [namespace :only [f1 f2]] form
(as they should).

Return value is a map {f1 ns1, f2 ns1, f3 ns2, ... }
"
  (let [
    use-expression (first (filter #(and (seq? %) (= :use (first %))) namespace))
    uses (rest use-expression)]
      (if (or (empty? uses) (not (vector? (first uses))))
        {}
        (into {} (mapcat #(if (= 1 (count %)) () (map (fn [used-fn] [used-fn (% 0)]) (% 2))) uses)))))

(defn parse-namespace-qualified-function [required-ns-lookup symbol-name]
  (let [
    string-name (str symbol-name)
    slash-idx (.indexOf string-name "/")]
      [(str (required-ns-lookup (symbol (.substring string-name 0 slash-idx)))) (.substring string-name (inc slash-idx))]))

(defn select-calls-in-def [def-names used-ns-lookup required-ns-lookup def-expression]
  (let [head (str (first (rest def-expression)))]
    (loop [
      body (flatten (rest (rest def-expression)))
      so-far []]
        (if (empty? body)
          [{:type :defn :name head} (distinct (filter identity so-far))]
          (let [
            current (first body)
            result (cond
               (some #{current} def-names) {:type :defn :name (str current)}
               (some #{current} (keys used-ns-lookup)) {:type :use :calls current :name (str (used-ns-lookup current))}
               (not (empty? (filter #(.startsWith (str current) (str % "/")) (keys required-ns-lookup))))  
                 (let [[namespace func] (parse-namespace-qualified-function required-ns-lookup current)]
                   {:type :require :calls func :name namespace})
               :else nil)]
            (recur (rest body) (conj so-far result)))))))

(defn select-calls-for-each-def [def-names used-ns-lookup required-ns-lookup defs]
  (into {} (map (partial select-calls-in-def def-names used-ns-lookup required-ns-lookup) defs)))

(defn select-defs-and-calls [code]
  (let [
    namespace (select-ns code)
    defs (select-defs code)
    def-names (into #{} (extract-def-names defs))
    used-ns-lookup (create-used-namespace-lookup namespace)
    required-ns-lookup (create-required-namespace-lookup namespace)]
      (select-calls-for-each-def def-names used-ns-lookup required-ns-lookup defs)))

(def ^:dynamic *formatting*
  { :defn
      {:shape "ellipse", :style "bold"}
    :use
      {:shape "box"}
    :require 
      {:shape "box"}
   [:defn :use] 
      { :penwidth 3, :style "dashed"}
   [:defn :require ]
      { :penwidth 3, :style "dotted"}
   [:defn :defn ]
      { :penwidth 3}})
    
(defn style
  ([key] (style key {}))
  ([key base-styling]
    (let [
      styling (merge base-styling (*formatting* key))]
      (if (nil? styling)
      ""
      (str "[" (join "," (for [[k v] styling] (str (name k) "=" v))) "]")))))

(defn q [st]
  (str "\"" st "\""))

;; this is a hack which enables multimethods to be redefined in the REPL
(def node-to-string nil)
(defmulti node-to-string 
  (fn [nodes>codes name referenced-names]
    (let [current (first referenced-names)]
      (current :type))))

(defmethod node-to-string :defn [nodes>codes name referenced-names]
  (str (nodes>codes name) " " (style :defn {:label (q name )}) ";\n"))
(defmethod node-to-string :use [nodes>codes name referenced-names]
  (let [label (q (str name "\\n\\n" (join "\\n" (map :calls referenced-names))))]
    (str (nodes>codes name) " " (style :use {:label label}) ";\n")))
(defmethod node-to-string :require [nodes>codes name referenced-names]
  (let [label (q (str name "\\n\\n" (join "\\n" (map :calls referenced-names))))]
    (str (nodes>codes name) " " (style :require {:label label}) ";\n")))

;; this is a hack which enables multimethods to be redefined in the REPL
(def edge-to-string nil)
(defmulti edge-to-string
  (fn [nodes>codes func call]
    [(func :type) (call :type)]))

(defmethod edge-to-string [:defn :use] [nodes>codes func call]
  (str (nodes>codes (func :name)) "->" (nodes>codes (call :name)) " " (style [:defn :use]) ";\n")) 
(defmethod edge-to-string [:defn :require] [nodes>codes func call]
  (str (nodes>codes (func :name)) "->" (nodes>codes (call :name)) " " (style [:defn :require]) ";\n"))
(defmethod edge-to-string [:defn :defn] [nodes>codes func call]
  (str (nodes>codes (func :name)) "->" (nodes>codes (call :name)) " " (style [:defn :defn]) ";\n"))

(defn identify-distinct-names-called [calls]
  "A referenced name can be a value in the current namespace, a name used from another namespace or a name referenced
   by a required namespace. In the last case, because the edge is from the name to the namespace, we only want
   one edge even if there are multiple names called in the namespace. This function removes duplicate names
   since for edges, only the namespace matters.
   
   Essentially, we are reducing the metadata about calls from :name, :type, and :calls to just :name and :type
   distincting."
  (distinct (map #(select-keys % [:name :type]) calls)))

(defn clj-to-dot [filename]
  (let [
    code (clj-to-data filename)
    namespace (second (select-ns code))
    defs-and-calls (select-defs-and-calls code)
    nodes (group-by :name (distinct (concat (keys defs-and-calls) (flatten (vals defs-and-calls)))))
    nodes>codes (into {} (map (fn [name num] [name (str "G" num)]) (keys nodes) (range)))
    codes>nodes  (map-invert nodes>codes)
    dot (StringBuffer.)]
      (.append dot "digraph g {\n")
      (.append dot "subgraph cluster1 {\n")
      (.append dot (str "label=\"" namespace "\"\n"))
      (doseq [[name referenced-names] nodes]
        (.append dot (node-to-string nodes>codes name referenced-names)))
      (.append dot "}\n")
      (doseq [[adef calls] defs-and-calls]
        (doseq [call (identify-distinct-names-called calls)]
          (.append dot (edge-to-string nodes>codes adef call))))
      (.append dot "}\n")
      [(str namespace) (str dot)]))

;; http://rosettacode.org/wiki/Walk_a_directory/Recursively#Clojure
(defn walk-directory [dirpath pattern]
  (doall (filter #(re-matches pattern (.getName %))
    (file-seq (file dirpath)))))

(defn generate-dot-files-from-clj-files [src-dir tar-dir]
  (doseq [file (walk-directory src-dir #".*\.clj")]
    (let [
      _ (println (.getPath file))
      [namespace dot] (clj-to-dot file)
      filename (str tar-dir "/" (.replace namespace "." "_") ".dot")]
        (spit filename dot))))
