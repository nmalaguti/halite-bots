(ns halite.durbinator
  (:require [halite.dsl :refer :all]
            [halite.borders :refer :all]
            [halite.moves :refer :all]
            [halite.map-basics :refer :all]
            [halite.map-analysis :refer :all]
            [clojure.set :as set]
            [halite.constants :refer :all]))

(defn-timed strategy
  "Main entry point to return the strategy"
  [game-map target-sites frame moves]
  (if moves
    {:moves moves}
    (let [starting-time (System/currentTimeMillis)
          num-owned (max 1 (count (filter is-mine? (:sites game-map))))
          game-map-no-borders (build-tracking-for-early-round game-map num-owned starting-time frame)
          next-to-enemy? (:next-to-enemy game-map-no-borders)
          enemy-targets-no-borders (->> (get-reachable-enemy-locations game-map-no-borders) (sort (compare-by :score asc :profit asc)))
          num-players (:num-players game-map-no-borders)
          border-enemies (take 3 (sort (compare-by :strength asc)
                                       (filter #(and (:border-site %) (not (:avoid %))) enemy-targets-no-borders)))

          ; game-map (add-border-distances game-map-no-borders next-to-enemy? num-players border-enemy)
          game-map (add-border-distances game-map-no-borders next-to-enemy? num-players)
          my-locations (if next-to-enemy?
                         (->> game-map :location-lookup vals
                            (filter is-mine?)
                            (sort (compare-by :strength desc :border-distance-exact asc :actual-enemy-distance asc)))
                         (->> game-map :location-lookup vals
                            (filter is-mine?)
                            (sort (compare-by :border-distance-exact asc :strength desc))))

          ; my-locations-no-borders (map #(:dissoc % :border-distance-exact)
          ;                              (filter #(> (get :border-distance-exact % infinity) distance-to-avoid-neutral-cells)
          ;                                      my-locations))
          my-locations-no-borders (filter is-mine? (vals (:location-lookup game-map-no-borders)))
          zero-strength-non-moves (build-moves-lookup game-map (mapv #(hash-map :loc % :dir :still :reason "No strength")
                                                                     (filter #(= 0 (:strength %)) my-locations))
                                                      nil)
          my-locations (remove #(already-moving? % zero-strength-non-moves) my-locations)

          front-line-moves (when next-to-enemy?
                             (reduce (fn [moves site]
                                       (let [new-moves (front-line-swap game-map site moves)]
                                         (if (seq new-moves)
                                           (build-moves-lookup game-map new-moves moves)
                                           moves)))
                                     zero-strength-non-moves
                                     (filter #(= 0 (:border-distance-exact %)) my-locations)))

          my-locations (remove #(already-moving? % front-line-moves) my-locations)
          my-locations-no-borders (remove #(already-moving? % front-line-moves) my-locations-no-borders)

          stand-strong-moves (when next-to-enemy?
                               (reduce (fn [moves site]
                                         (build-moves-lookup game-map [{:loc site :dir :still :reason "Standing strong, avoiding overkill"}]
                                                             moves))
                                       front-line-moves
                                       (filter #(surrounding-site-in-battle? game-map % moves) my-locations)))
          my-locations (remove #(already-moving? % stand-strong-moves) my-locations)

          num-border-troops (get-border-troop-buffer num-players)
          ; num-border-troops 5
          border-crossed-atom (atom false)
          map-and-moves (if next-to-enemy?
                           (reduce (fn [{:keys [moves new-map]} site]
                                     (let [distance (get site :border-distance-exact infinity)]
                                       (if (< distance num-border-troops)
                                         (let [{:keys [direction border-crossed]} (get-attack-direction new-map site moves num-players next-to-enemy? true)]
                                           (when border-crossed
                                             (reset! border-crossed-atom border-crossed))
                                           (let [updated-map new-map]
                                                ;  (if (= 0 distance) (tag-avoid-enemy new-map site direction) new-map)]
                                             {:moves (build-moves-lookup new-map [{:loc site :dir direction :reason "Close to enemy, getting closer"}]
                                                                         moves)
                                              :new-map updated-map}))
                                         {:moves moves :new-map new-map})))
                                   {:moves stand-strong-moves :new-map game-map}
                                   my-locations)
                           stand-strong-moves)
          enemy-kill-moves (:moves map-and-moves)
          game-map (:new-map map-and-moves)
          my-locations (remove #(already-moving? % enemy-kill-moves) my-locations)
          my-locations-no-borders (remove #(already-moving? % enemy-kill-moves) my-locations-no-borders)

          ; border-moves (if (seq border-enemies)
          ;               ;  (first (get-moves-for-target game-map target my-locations existing-moves number-to-visit extra-strength extra-units))
          ;                (:moves (move-towards-border-sites game-map game-map-no-borders my-locations-no-borders border-enemies next-to-enemy? enemy-kill-moves num-owned num-players false frame true))
          ;                enemy-kill-moves)

          border-moves enemy-kill-moves

          my-locations (remove #(already-moving? % border-moves) my-locations)
          enemy-targets-no-borders (if next-to-enemy?
                                     (remove (fn [site]
                                               (let [ss (get-surrounding-sites game-map site)]
                                                 (seq (keep #(when (< (get % :border-distance-exact infinity) distance-to-avoid-neutral-cells)
                                                               1)
                                                            ss))))
                                             enemy-targets-no-borders)
                                     enemy-targets-no-borders)
          response (if (seq enemy-targets-no-borders)
                     (new-moves-setup game-map game-map-no-borders my-locations-no-borders enemy-targets-no-borders next-to-enemy? border-moves num-owned num-players false frame false)
                    ;  (new-moves-setup game-map game-map-no-borders my-locations-no-borders enemy-targets-no-borders next-to-enemy? border-moves num-owned num-players (not (:strongest-player game-map)) frame false)
                    ;  (move-towards-best-sites game-map game-map-no-borders my-locations-no-borders enemy-targets-no-borders next-to-enemy? border-moves num-owned num-players (not (:strongest-player game-map)) frame false)
                     {:moves border-moves})
          ; _ (log! (str "The response was:" (pr-str response)))
          ; _ (new-moves-setup game-map game-map-no-borders my-locations-no-borders enemy-targets-no-borders next-to-enemy? border-moves num-owned num-players (not (:strongest-player game-map)) frame false)
          targeted-moves (:moves response)
          ; enemy-targets-no-borders (remove #(or (:border-site %) (:avoid %))
                                          ;  (:targets response))
          enemy-targets-no-borders (remove :avoid (:targets response))
          _ (log! (str "Frame: " frame ", next-to-enemy: " next-to-enemy?))
          my-locations (remove #(already-moving? % targeted-moves) my-locations)
          game-map (if next-to-enemy?
                     game-map
                    ;  (add-border-distances game-map-no-borders next-to-enemy? num-players (take (max-num-border-sites enemy-targets-no-borders (or next-to-enemy? (:near-enemy game-map)) (:strongest-player game-map) num-owned (:total-strength game-map) enemy-targets-no-borders true)))
                     (add-border-distances game-map-no-borders next-to-enemy? num-players enemy-targets-no-borders true))
                    ;  (add-border-distances game-map-no-borders next-to-enemy? num-players [(first enemy-targets-no-borders)] true))
          my-locations (if next-to-enemy?
                         my-locations
                         (remove #(already-moving? % targeted-moves)
                                 (filter #(and (is-mine? %)
                                               (not (should-stay-still? game-map % next-to-enemy? num-players num-owned targeted-moves)))
                                         (vals (:location-lookup game-map)))))
          leftover-moves (reduce (fn [moves site]
                                   (let [direction (if (or (and (:near-enemy game-map) (not next-to-enemy?)
                                                                (< (get site :border-distance-exact infinity) 1)
                                                                (not (site-strength-over-255? moves site (:strength site))))
                                                           (and (nil? (:border-distance-exact site))
                                                                (not (site-strength-over-255? moves site (:strength site))))
                                                           (should-stay-still? game-map site next-to-enemy? num-players num-owned moves))
                                                     :still
                                                     (move-towards-border game-map site moves next-to-enemy?))]
                                     (build-moves-lookup game-map [{:loc site :dir direction :reason "Leftover"}] moves)))
                                 targeted-moves
                                 (remove #(= 0 (:strength %)) my-locations))
          final-moves (sanitize-moves game-map leftover-moves)]
        {:moves final-moves
         :raw-moves leftover-moves})))

(defn-timed init
  "Build up the memoized lookup."
  [game-map]
  (build-memoized-lookup game-map)
  (let [moves (:moves (strategy game-map nil 0 nil))]
    {:strategy strategy
     :moves moves}))
