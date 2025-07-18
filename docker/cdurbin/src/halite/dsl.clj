(ns halite.dsl
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)))

(defn log!
  [txt]
  (spit "V122-master.log" (str txt "\n") :append true))

(defmacro defn-timed
  "Creates a function that logs how long it took to execute the body. It supports multiarity functions
  but only times how long the last listed arity version takes. This means it should be used with
  multiarity functions where it calls itself with the extra arguments."
  [fn-name & fn-tail]
  (let [fn-name-str (name fn-name)
        ns-str (str *ns*)
        ;; Extract the doc string from the function if present
        [doc-string fn-tail] (if (string? (first fn-tail))
                               [(first fn-tail) (next fn-tail)]
                               [nil fn-tail])
        ;; Wrap single arity functions in a list
        fn-tail (if (vector? (first fn-tail))
                  (list fn-tail)
                  fn-tail)
        ;; extract other arities defined in the function which will not be timed.
        other-arities (drop-last fn-tail)
        ;; extract the last arity definitions bindings and body
        [timed-arity-bindings & timed-arity-body] (last fn-tail)]
    `(defn ~fn-name
       ~@(when doc-string [doc-string])
       ~@other-arities
       (~timed-arity-bindings
         (let [start# (System/currentTimeMillis)]
           (try
             ~@timed-arity-body
             (finally
               (let [elapsed# (- (System/currentTimeMillis) start#)]
                 (log! (format
                          "%s/%s took %d ms." ~ns-str ~fn-name-str elapsed#))))))))))

(def uglify-time-unit
  "Create a map of pretty keywords to ugly TimeUnits"
  (into {} (for [[enum aliases] {TimeUnit/NANOSECONDS [:ns :nanoseconds]
                                 TimeUnit/MICROSECONDS [:us :microseconds]
                                 TimeUnit/MILLISECONDS [:ms :milliseconds]
                                 TimeUnit/SECONDS [:s :sec :seconds]}
                 alias aliases]
             {alias enum})))

(defn thunk-timeout
  "Takes a function and an amount of time to wait for thse function to finish
   executing. The sandbox can do this for you. unit is any of :ns, :us, :ms,
   or :s which correspond to TimeUnit/NANOSECONDS, MICROSECONDS, MILLISECONDS,
   and SECONDS respectively."
  ([thunk ms]
   (thunk-timeout thunk ms :ms nil)) ; Default to milliseconds, because that's pretty common.
  ([thunk time unit]
   (thunk-timeout thunk time unit nil))
  ([thunk time unit tg]
   (let [task (FutureTask. thunk)
         thr (if tg (Thread. tg task) (Thread. task))]
     (try
       (.start thr)
       (.get task time (or (uglify-time-unit unit) unit))
       (catch TimeoutException e
         (.cancel task true)
         (.stop thr)
         (throw (TimeoutException. "Execution timed out.")))
       (catch Exception e
         (.cancel task true)
         (.stop thr)
         (throw e))
       (finally (when tg (.stop tg)))))))

(defmacro with-timeout [time & body]
  `(thunk-timeout (fn [] ~@body) ~time))

;; Sorting
(def asc compare)
(def desc #(compare %2 %1))

(defn compare-by
  "Compare items based on values of the provided keys and which direction to sort.
  Example: (compare-by :last-name asc :date-of-birth desc)."
  [& key-cmp-pairs]
  (fn [x y]
      (loop [[k cmp & more] key-cmp-pairs]
         (let [result (cmp (k x) (k y))]
              (if (and (zero? result) more)
                  (recur more)
                  result)))))

(defn avg
  "Returns the average value for a collection"
  [coll]
  (apply / (reduce (fn [[sum n] x] [(+ sum x) (inc n)]) [0 0] coll)))

(defn avg-no-zeros
  "Returns the average value for a collection"
  [coll]
  (let [coll (remove #(= 0 %) coll)]
    (if (seq coll)
      (apply / (reduce (fn [[sum n] x] [(+ sum x) (inc n)]) [0 0] coll))
      0)))
