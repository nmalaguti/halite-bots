(ns halite.core
  (:require [clojure.java.data :refer :all]
            [halite.interop :refer :all]
            [halite.dsl :refer [log! with-timeout]]
            [halite.durbinator :as durbinator]
            [halite.map-basics :refer :all])
  (:gen-class))

(def best-strategy (atom durbinator/strategy))
(def init durbinator/init)

(def frame (atom 0))
(def target-sites (atom nil))
(def first-moves (atom nil))

(defn -main
  [& args]
  (let [{:keys [id game-map]} (get-init)]
    (set-id id)
    (try
      (with-timeout 4000
        (let [{:keys [strategy moves]} (init game-map)]
              ; _ (log! (str "Sites are " game-map))]
          (reset! best-strategy strategy)
          (reset! first-moves moves)))
      (catch Exception e
        (log! (str e))
        (log! "Init timed out"))))

  (println "cdurbin")

  (while true
    (swap! frame inc)
    (when (= @frame 26) (reset! best-strategy durbinator/strategy))
    (when (= @frame 2) (reset! first-moves nil))
    (let [game-map (get-frame)
          {:keys [moves targets]} (@best-strategy game-map @target-sites @frame @first-moves)]
        ; (log! (str "Frame: " @frame ", Moves: " (pr-str (remove #(= :still (:dir %)) moves))))
      (send-moves moves))))

(comment
 (do
  (set-id 1)
  (require '[criterium.core])
  (criterium.core/with-progress-reporting
   (criterium.core/quick-bench (time (count (durbinator/strategy (clojure.edn/read-string (slurp (clojure.java.io/resource "big-map-V49.edn"))) nil 232 nil)))))))
