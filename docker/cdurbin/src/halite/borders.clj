(ns halite.borders
  (:require
   [halite.dsl :refer :all]
   [halite.map-basics :refer :all]
   [halite.constants :refer :all]))

(defn get-border-troop-buffer
  "Returns the distance to use for border troops. TODO - maybe look at neutral percent as well."
  [num-players]
  10)

(defn- stop-at-strength
  "Returns the number of enemy sites to target based on strength we will spend."
  [enemy-locations allowed-strength]
  (:num-enemies (reduce (fn [{:keys [strength-left num-enemies]} enemy]
                          (let [remaining-strength (- strength-left (:strength enemy))]
                            (if (<= remaining-strength 0)
                              (reduced {:num-enemies (inc num-enemies)})
                              {:strength-left remaining-strength :num-enemies (inc num-enemies)})))
                        {:strength-left allowed-strength :num-enemies 0}
                        enemy-locations)))

(defn max-num-border-sites
  "Returns the maximum number of border sites to target."
  [enemy-locations next-to-or-near-enemy? strongest-player? num-owned my-strength]
  (if next-to-or-near-enemy?
    (if (< num-owned charge-count)
      (stop-at-strength enemy-locations (* my-strength 0.08))
      (if strongest-player?
        (Math/ceil (* 2 (/ (count enemy-locations) 3)))
        (min (stop-at-strength enemy-locations (* my-strength 0.20)) (Math/ceil (/ (count enemy-locations) 5)))))
    (max 5 (Math/ceil (* 2 (/ (count enemy-locations) 3))))))
    ; (max 2 (Math/ceil (* 0.7 (count enemy-locations))))))

(defn visit-neighbors
  "Add border-distance to all neighbors"
  [game-map site to-visit-directions max-exact-distance]
  (reduce (fn [game-map direction]
            (let [existing-site (get-site game-map site direction)]
              (if-not (is-mine? existing-site)
                game-map
                (if (or (<= (get existing-site :border-distance-exact infinity) (inc (:border-distance-exact site)))
                        (and max-exact-distance (> (get site :border-distance-exact) max-exact-distance)))
                  game-map
                  (let [updated-site (assoc existing-site :border-distance-exact (inc (:border-distance-exact site)))
                        updated-game-map (assoc-in game-map
                                                   [:location-lookup {:x (:x existing-site) :y (:y existing-site)}]
                                                   updated-site)
                        to-visit-directions (remove #(= (opposite-direction direction) %) directions)]
                    (visit-neighbors updated-game-map updated-site to-visit-directions max-exact-distance))))))
          game-map
          to-visit-directions))

(defn visit-neighbors-tracking-locations
  "Add border-distance to all neighbors"
  [game-map site to-visit-directions max-exact-distance visited-locations]
  (reduce (fn [{:keys [game-map visited-locations]} direction]
            (let [existing-site (get-site game-map site direction)]
              (if-not (is-mine? existing-site)
                {:game-map game-map :visited-locations visited-locations}
                (if (or (<= (get existing-site :border-distance-exact infinity) (inc (:border-distance-exact site)))
                        (and max-exact-distance (> (get site :border-distance-exact) max-exact-distance)))
                  {:game-map game-map :visited-locations visited-locations}
                  (let [updated-site (assoc existing-site :border-distance-exact (inc (:border-distance-exact site)))
                        updated-game-map (assoc-in game-map
                                                   [:location-lookup {:x (:x existing-site) :y (:y existing-site)}]
                                                   updated-site)
                        to-visit-directions (remove #(= (opposite-direction direction) %) directions)
                        visited-locations (conj visited-locations updated-site)]
                    (visit-neighbors-tracking-locations updated-game-map updated-site to-visit-directions max-exact-distance visited-locations))))))
          {:game-map game-map :visited-locations visited-locations}
          to-visit-directions))

(defn get-distance-to-non-zero-enemy
  "Returns the distance to a non-zero strength enemy for any site with border distance exact of 0"
  [game-map site]
  (apply min (for [surrounding-site (get-surrounding-sites game-map site)
                   second-order-site (get-surrounding-sites game-map surrounding-site)]
                 (if (and (is-enemy? second-order-site) (> (:strength second-order-site) 0))
                   1
                   2))))

(defn-timed add-border-distances
  "Adds border distances to all of my cells."
  ([game-map next-to-enemy? num-players]
   (add-border-distances game-map next-to-enemy? num-players nil))
  ([game-map next-to-enemy? num-players border-enemy]
   (let [border-sites (if next-to-enemy?
                        (filter #(and (is-mine? %) (any-enemies? (get-surrounding-sites game-map %)))
                                (vals (:location-lookup game-map)))
                        (if border-enemy
                          [border-enemy]
                          (filter #(and (is-mine? %) (next-to-any-enemies? game-map %))
                                  (vals (:location-lookup game-map)))))]
     (add-border-distances game-map next-to-enemy? num-players border-sites (and (not next-to-enemy?) border-enemy))))
  ([game-map next-to-enemy? num-players border-sites enemy-sites?]
   (let [border-sites (if enemy-sites?
                        (mapcat (fn [enemy-site]
                                  (filter is-mine?
                                          (get-surrounding-sites game-map enemy-site)))
                                border-sites)
                        border-sites)
         {:keys [new-game-map updated-sites]} (reduce (fn [{:keys [new-game-map updated-sites]} border-site]
                                                        (let [actual-enemy-distance (if next-to-enemy?
                                                                                      (get-distance-to-non-zero-enemy new-game-map border-site)
                                                                                      infinity)
                                                              updated-site (assoc border-site :border-distance-exact 0 :actual-enemy-distance actual-enemy-distance)
                                                              updated-game-map (assoc-in new-game-map
                                                                                         [:location-lookup {:x (:x border-site) :y (:y border-site)}]
                                                                                         updated-site)]
                                                          {:new-game-map updated-game-map :updated-sites (conj updated-sites updated-site)}))
                                                      {:new-game-map game-map}
                                                      border-sites)
         border-sites (if enemy-sites?
                        updated-sites
                        (if next-to-enemy?
                            (filter #(and (is-mine? %) (any-enemies? (get-surrounding-sites new-game-map %)))
                                    (vals (:location-lookup new-game-map)))
                            (filter #(and (is-mine? %) (next-to-any-enemies? new-game-map %))
                                    (vals (:location-lookup new-game-map)))))
         new-game-map (reduce (fn [game-map border-site]
                                (visit-neighbors game-map border-site directions max-distance-to-move))
                              new-game-map
                              border-sites)]
     new-game-map)))

(defn unbroken-border-site?
  "Returns true if the site is on an unbroken border."
  [game-map site owners-to-attack]
  (and (is-neutral? site)
       (seq (for [directly-surrounding-site (get-surrounding-sites game-map site)
                  :when (and (is-enemy? directly-surrounding-site)
                             (not (contains? owners-to-attack (:owner directly-surrounding-site))))]
              1))))

(defn avoid-location?
  "Returns true if a location should be avoided."
  [game-map site next-to-enemy? should-attack owners-to-attack]
  (and
       (not should-attack)
       (> (:num-players game-map) 2)
       (is-neutral? site)
       (if next-to-enemy?
         (seq (for [directly-surrounding-site (get-surrounding-sites game-map site)
                    :let [owner (:owner directly-surrounding-site)]
                    :when (and (not= 0 owner)
                               (not= @my-id owner)
                              ;  (not (contains? (:enemies-with-borders game-map) owner))
                               (not (contains? owners-to-attack owner)))]
                1))
         (seq (for [directly-surrounding-site (get-surrounding-sites game-map site)
                    next-order-surrounding-site (get-surrounding-sites game-map directly-surrounding-site)
                    :let [owner (:owner directly-surrounding-site)
                          next-owner (:owner next-order-surrounding-site)]
                    :when (or (and (not= 0 owner)
                                   (not= @my-id owner)
                                  ;  (not (contains? (:enemies-with-borders game-map) owner))
                                   (not (contains? owners-to-attack owner)))
                              (and (not= 0 next-owner)
                                   (not= @my-id next-owner)
                                  ;  (not (contains? (:enemies-with-borders game-map) next-owner))
                                   (not (contains? owners-to-attack next-owner))))]
                1)))))

(defn site-strength-over-n?
  "Returns true if I am moving to this site and will have over 255 strength."
  [moves site strength n]
  (< n (apply + strength (->> (get (:dest-lookup moves) {:x (:x site) :y (:y site)}
                                   [{:loc {:strength 0}}])
                              (map :loc)
                              (map :strength)))))

(defn in-diamond-formation?
  "Returns true if the move would still make it possible to be in diamond formation."
  [game-map moves site]
  (let [dist (get site :border-distance-exact infinity)]
    (or (> dist 8)
        (<= dist 0)
        (> (:strength site) 200)
        (empty? (filter #(site-strength-over-n? moves % 0 15) (get-surrounding-sites game-map site))))))

(defn surrounding-site-in-battle?
  "Returns true if there is a neighboring site about to go into battle."
  [game-map site moves]
  (or
      (and (= 0 (:border-distance-exact site))
           (>= (count (filter is-enemy? (get-surrounding-sites game-map site))) 4))

      (and (= 1 (:border-distance-exact site))
           ;; There's a surrounding strong enemy.
           (seq (for [neighboring-site (filter #(= 0 (:border-distance-exact %)) (get-surrounding-sites game-map site))
                      two-sites-away (filter is-enemy? (get-surrounding-sites game-map neighboring-site))
                      the-one-that-matters (filter #(and (is-enemy? %) (> (:strength %) 10)) (get-surrounding-sites game-map two-sites-away))
                      :when (and (empty? (filter #(site-strength-over-n? moves % 0 15) (get-surrounding-sites game-map site)))
                                 (< 255 (+ (:strength site) (:strength neighboring-site))))]
                  1)))))

(defn guess-enemy-moves
  "Returns a game-map replacing all of the enemy locations with an imaginary piece that is the sum
  of the strength of all the enemies."
  [game-map enemy-locations]
  (reduce (fn [gm site]
            (assoc-in gm [:location-lookup {:x (:x site) :y (:y site)} :strength]
                      (reduce + (map :strength (filter is-enemy? (get-surrounding-sites gm site))))))
          game-map
          enemy-locations))

(defn avoid-double-overkill
  "Marks sites to avoid to prevent getting taking significant overkill damage."
  [game-map enemy-site]
  (reduce (fn [gm ss]
            (assoc-in gm [:location-lookup {:x (:x ss) :y (:y ss)} :avoid] true))
          game-map
          (get-surrounding-sites game-map enemy-site)))

(defn tag-avoid-enemy
  "Marks site to avoid."
  [game-map site direction]
  (let [avoid-location (get-site game-map site direction)]
    (avoid-double-overkill game-map avoid-location)))
