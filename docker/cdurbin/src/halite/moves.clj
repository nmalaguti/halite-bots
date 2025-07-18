(ns halite.moves
  (:require
   [halite.dsl :refer :all]
   [halite.map-basics :refer :all]
   [halite.map-analysis :refer :all]
   [halite.borders :refer :all]
   [halite.constants :refer :all]))

(defn build-source-moves-lookup
  "Build fast-lookup for moves"
  [game-map new-moves existing-moves-map]
  (reduce (fn [moves-map move]
            (update moves-map :source-lookup assoc {:x (:x (:loc move))
                                                    :y (:y (:loc move))}
                    move))
          existing-moves-map
          new-moves))

(defn build-moves-lookup
  "Build fast-lookup for moves"
  [game-map new-moves existing-moves-map]
  (reduce (fn [moves-map move]
            (let [moves-map (update moves-map :source-lookup assoc {:x (:x (:loc move))
                                                                    :y (:y (:loc move))}
                                    move)
                  dest (get-site game-map (:loc move) (:dir move))]
              (update-in moves-map [:dest-lookup {:x (:x dest) :y (:y dest)}] #(conj % move))))
          existing-moves-map
          new-moves))

(defn get-moves-at-destination
  "Returns all of the moves at a destination."
  [moves dest]
  (get-in moves [:dest-lookup {:x (:x dest) :y (:y dest)}]))

(defn remove-moves-at-destination
  "Removes a move from the list of moves."
  [moves dest]
  (assoc-in moves [:dest-lookup {:x (:x dest) :y (:y dest)}] nil))

(defn already-moving?
  "Returns true if the passed in location is already contained in moves."
  [site moves]
  (get (:source-lookup moves) {:x (:x site) :y (:y site)}))

(defn should-stay-still?
  "Returns whether a cell should stay still."
  [game-map {:keys [production strength border-distance-exact] :as site} next-to-enemy? num-players num-owned moves]
  (and (> production 0)
       (if next-to-enemy?
           (or (< strength 15) (< (/ strength production)
                                  (if (= 2 num-players) 7 8)))
           (< (/ strength production) 6))
       (not (site-strength-over-255? moves site (+ strength production)))))

(defn safe-move-location?
  "Returns true if a site is either owned by us or has a strength of 0"
  [site]
  (and (not (:avoid site))
       (or (is-mine? site) (= 0 (:strength site)))))

(defn get-enemy-kill-direction
  "Returns the direction to move to kill an enemy site - if not near an enemy then kill neutral.
  Returns nil if nothing to kill."
  [game-map moves site border-already-crossed]
  (let [surrounding-sites (get-surrounding-sites game-map site)
        distance (get site :border-distance-exact infinity)
        ;; TODO Test this fix to see if it fixes the standing still issue when near the enemy
        ;; TODO factor these checks out to only happen once during tracking instead of for every time
        include-neutral? (or (not (:next-to-enemy game-map))
                             (= (:num-players game-map) 2)
                             (< distance-to-avoid-neutral-cells distance))
        neutral-profit (* 4 (inc distance))
        neutral-score (* 4 (inc distance))
        enemy-sites (if include-neutral?
                      (->> surrounding-sites
                           (remove is-mine?)
                           (remove #(and (is-neutral? %) (> (:profit %) neutral-profit) (> (:score %) neutral-score)))
                           (filter #(> (:strength site) (:strength %))))
                      (when (> (:strength site) 9)
                        (if (and (= 1 distance) (not (should-stay-still? game-map site true 3 infinity moves)))
                          (->> surrounding-sites
                               (filter #(= 0 (:border-distance-exact %))))
                          (->> surrounding-sites
                               (filter is-enemy?)))))
        target-site (some->> (seq enemy-sites)
                             (remove :avoid)
                             (remove #(and border-already-crossed (:border-site %)))
                             (remove #(site-strength-over-255? moves % (:strength site)))
                             (filter #(in-diamond-formation? game-map moves %))
                             (map #(add-enemy-stats game-map % site))
                             (sort (compare-by :total-production desc :total-strength desc :ecount desc :my-count asc :neutral-count desc))
                             first)]
    (when target-site
      {:direction (get-direction-to-site site target-site)
       :border-crossed (:border-site target-site)})))

(defn move-towards-border
  "Move towards a border site."
  [game-map site moves next-to-enemy?]
  (let [surrounding-sites (get-surrounding-sites game-map site)
        to-site (if next-to-enemy?
                  (->> surrounding-sites
                       (filter safe-move-location?)
                       (remove #(site-strength-over-255? moves % (:strength site)))
                       (filter #(in-diamond-formation? game-map moves %))
                       (remove #(< 1 (get site :border-distance-exact (dec infinity)) (get % :border-distance-exact infinity)))
                       (sort (compare-by :border-distance-exact asc :production asc))
                       first)
                  (->> surrounding-sites
                       (filter safe-move-location?)
                       (remove #(site-strength-over-255? moves % (:strength site)))
                       (sort (compare-by :border-distance-exact asc :production asc))
                       first))
        to-site-with-neutral to-site
        to-site                  (if (and next-to-enemy?
                                          (nil? to-site-with-neutral)
                                          (site-strength-over-255? moves site (+ (:production site) (:strength site))))
                                   (->> surrounding-sites
                                        (filter safe-move-location?)
                                        (remove #(site-strength-over-255? moves % (:strength site)))
                                        (sort (compare-by :border-distance-exact desc :production asc))
                                        first)
                                   to-site-with-neutral)
        to-site-with-neutral (if (and next-to-enemy?
                                      (nil? to-site)
                                      (site-strength-over-255? moves site (+ (:strength site) (:production site))))
                                (->> surrounding-sites
                                     (filter #(in-diamond-formation? game-map moves %))
                                     (filter is-neutral?)
                                     (remove :avoid)
                                     (filter #(> (:strength site) (:strength %)))
                                     (remove #(site-strength-over-255? moves % (:strength site)))
                                     (sort (compare-by :strength asc))
                                     first)
                                to-site)]

    (if (not to-site-with-neutral)
      :still
      (get-direction-to-site site to-site-with-neutral))))


(defn move-towards-border-simple
  "Move towards a border site."
  [game-map site moves next-to-enemy?]
  (let [surrounding-sites (get-surrounding-sites game-map site)
        to-site (->> surrounding-sites
                     (filter safe-move-location?)
                     (remove #(site-strength-over-255? moves % (:strength site)))
                     (sort (compare-by :border-distance-exact asc :production asc))
                     first)]
    (if (not to-site)
      :still
      (get-direction-to-site site to-site))))

(defn move-towards-border-force
  "Move towards a border site."
  [game-map site moves next-to-enemy?]
  (let [to-site         (some->> (get-surrounding-sites game-map site)
                                 (filter safe-move-location?)
                                 (sort (compare-by :border-distance-exact asc :production asc))
                                 first)
        dir (if (or (not to-site)
                    (and (< (get site :border-distance-exact (dec infinity)) (get to-site :border-distance-exact infinity))
                         (not (site-strength-over-255? moves site (:strength site)))))
              :still
              (get-direction-to-site site to-site))]
    (if to-site
      (if (site-strength-over-255? moves to-site (:strength site))
        ;; Remove the old move and replace it with move towards border call
        (do
         (let [old-moves (get-moves-at-destination moves to-site)
               new-moves (remove-moves-at-destination moves to-site)
               updated-moves (for [move old-moves
                                   :when (> (:strength (:loc move)) 0)]
                               {:loc (:loc move) :dir (move-towards-border-simple game-map (:loc move) new-moves next-to-enemy?) :reason "Crazy replacement"})
               updated-moves (build-moves-lookup game-map updated-moves new-moves)]
           {:loc site :dir dir :moves updated-moves}))
        {:loc site :dir dir :moves moves})
      {:loc site :dir dir :moves moves})))

(defn get-moves-for-target
  "Returns the moves in order to go after a target."
  [game-map target my-locations existing-moves number-to-visit extra-strength extra-units]
  ; (log! (str "DEBUG target" target "my locations" my-locations))
  (let [target-strength (min 235 (+ extra-strength (:strength target)))
        my-sites (->> my-locations
                      (remove #(already-moving? % existing-moves))
                      (filter #(<= (get % :border-distance-exact infinity) number-to-visit))
                      (remove #(= 0 (:strength %)))
                      (sort (compare-by :border-distance-exact asc :strength desc)))
        ; _ (log! (str "DEBUG my sites after filter:" my-sites))
        {:keys [distance last-site overkill]}
        (reduce (fn [{:keys [total-strength distance per-round-strength last-distance last-site overkill]} site]
                  (if-not distance
                    (let [current-distance (:border-distance-exact site)
                          total-strength (if (= current-distance last-distance)
                                           (+ total-strength (:strength site))
                                           (+ total-strength (:strength site) per-round-strength))]
                      (if (> (if (= current-distance last-distance)
                                total-strength
                                (+ total-strength (:production site)))
                             target-strength)
                        (reduced
                          {:distance (if (> total-strength target-strength) current-distance (inc current-distance))
                           :last-site site
                           :overkill (- total-strength target-strength)})
                        {:total-strength total-strength
                         :per-round-strength (+ per-round-strength (:production site))
                         :last-distance current-distance}))
                    {:distance distance
                     :last-site last-site
                     :overkill overkill}))
                {:total-strength 0 :per-round-strength 0 :last-distance 1 :overkill -1}
                my-sites)
        distance (or distance infinity)
        breakout-atom (atom false)
        breakout-count (atom extra-units)
        production-used-atom (atom 0)
        my-sites (for [site my-sites
                       :while (>= @breakout-count 0)]
                   (do
                    (swap! production-used-atom #(+ % (:production site)))
                    (when (and (= (:x last-site) (:x site))
                               (= (:y last-site) (:y site)))
                      (reset! breakout-atom true))
                    (when @breakout-atom
                      (swap! breakout-count dec))
                    site))

        my-sites (sort-by :strength my-sites)

        my-sites (if (and (seq my-sites) (= 0 extra-units))
                   (loop [remaining-overkill (or overkill -1)
                          sites my-sites]
                     (let [site (first sites)
                           strength (if site
                                      (+ (:strength site) (* (:production site))
                                                          (dec (:border-distance-exact site)))
                                      infinity)]
                       (if (> remaining-overkill strength)
                         (do
                           (swap! production-used-atom #(- % (:production site)))
                           (recur (- remaining-overkill strength)
                                  (remove #(and (= (:x %) (:x site))
                                                (= (:y %) (:y site)))
                                          sites)))
                         sites)))
                   my-sites)

        moves (reduce (fn [moves-map site]
                        (let [my-distance (:border-distance-exact site)
                              moves (if (> (:strength site) 0)
                                      (if (and (= my-distance distance) (not= distance infinity))
                                        (if (= my-distance 1)
                                          {:loc site :dir (get-direction-to-site site target) :reason "get-moves-for-target distance 1"}
                                          (move-towards-border-force game-map site (:all moves-map) false))
                                        (if (site-strength-over-255? (:all moves-map) site (:strength site))
                                          ;; Get out of the way of other people
                                          {:loc site :dir (move-towards-border-simple game-map site (:all moves-map) false) :reason "get-moves-for-target I need to get out of the way."}
                                          ;; Wait for others to join the train
                                          {:loc site :dir :still :reason "get-moves-for-target waiting for others"}))
                                      {:loc site :dir :still :reason "I have no strength"})]
                          {:all (build-moves-lookup game-map [(dissoc moves :moves)] (or (:moves moves) (:all moves-map)))
                           :new-only (conj! (:new-only moves-map) (dissoc moves :moves))}))
                      {:all existing-moves :new-only (transient [])}
                      (sort (compare-by :strength desc :border-distance-exact asc) my-sites))]
    [(:all moves) (persistent! (:new-only moves)) (not= infinity distance) @production-used-atom]))

(defn get-distance-to-attack
  "Returns the distance to attack for the given site."
  [game-map site avg-score _ _ _]
  (if-let [site-score (:score site)]
    (cond
      (>= site-score (* avg-score 3)) 0
      (<= site-score (/ avg-score 4)) 7
      (<= site-score avg-score) 3
      :else 2)
    2))

(defn get-enemy-locations-worth-taking
  "Returns enemy locations we should target. Try to implement this algorithm:
  when next to enemy the profit of a site needs to be <= (distance + 1) * 2."
  [game-map enemy-locations-in border-crossed ignore-profit? max-sites next-to-enemy?]
  (let [enemy-locations (->> (if border-crossed
                               (remove :border-site enemy-locations-in)
                               enemy-locations-in)
                             (remove :avoid)
                             (sort (compare-by :score asc :profit asc)))
        enemy-locations (if next-to-enemy?
                          (filter (fn [enemy-site]
                                    (let [min-distance (apply min infinity (map #(get % :border-distance-exact infinity)
                                                                                (filter is-mine? (get-surrounding-sites game-map enemy-site))))]
                                      ; (log! (str "The min distance is: " min-distance " for " enemy-site))
                                      (or
                                       (<= (:profit enemy-site) (* 1.5 (inc min-distance)))
                                       (<= (:score enemy-site) (* 0.9 (inc min-distance))))))
                                  (filter is-neutral? enemy-locations))
                          (if (and (:near-enemy game-map) (not ignore-profit?))
                            (remove #(and (> (:profit %) 15)
                                          (> (or (:score %) 0) 50))
                                    enemy-locations)
                            enemy-locations))
        avg-score (if (and (not next-to-enemy?) (seq enemy-locations))
                    (avg (map :score enemy-locations))
                    0)
        enemy-locations (if (not= 0 avg-score)
                          (filter #(< (get % :score infinity) (* 2 avg-score)) enemy-locations)
                          enemy-locations)]
    enemy-locations))

(defn get-attack-direction
  "Return the direction to move in an attempt to attack the nearest enemy or move closer to them."
  [game-map site moves num-players next-to-enemy? border-already-crossed]
  (if-let [kill-direction (when (> (:strength site) 0)
                            (get-enemy-kill-direction game-map moves site border-already-crossed))]
    kill-direction
    (if (should-stay-still? game-map site true num-players infinity moves)
      {:direction :still}
      {:direction (move-towards-border game-map site moves next-to-enemy?)})))

(defn sanitize-moves
  "Goes through all the locations and tries to minimize collapsing two cells to more than 255 strength"
  [game-map moves]
  (doseq [destination-site (vals (:dest-lookup moves))]
    (let [strength-at-site (reduce + (map #(get-in % [:loc :strength]) destination-site))]
      (when (> strength-at-site 255)
        (log! (str "Oh no strength is " strength-at-site "for " destination-site)))))
  (->> moves :dest-lookup vals flatten (remove nil?)
       (remove #(= 0 (:loc (:strength %))))))

(defn-timed decorate-my-locations-with-best-sites
  "Adds :best-sites tracking to my locations. Number to visit is purely to prevent timeouts."
  [game-map my-locations enemy-locations]
  (let [num-locations (max 1 (count enemy-locations))]
    (loop [iteration 1
           game-map game-map
           enemy-locations enemy-locations]
      (let [percent (/ iteration num-locations)
            number-to-visit (cond
                              (< num-locations 20) 7
                              (<= percent 0.1) 7
                              (<= percent 0.25) 6
                              (<= percent 0.4) 5
                              (<= percent 0.50) 4
                              (<= percent 0.75) 3
                              :else 2)

            next-enemy-target (assoc (first enemy-locations) :border-distance-exact 0)
            temp-game-map (assoc-in game-map [:location-lookup {:x (:x next-enemy-target)
                                                                :y (:y next-enemy-target)}]
                                    next-enemy-target)
            {my-locations-visited :visited-locations} (visit-neighbors-tracking-locations temp-game-map next-enemy-target directions number-to-visit [])
            new-game-map (reduce (fn [gm site]
                                   (let [num-sites-to-keep (/ (:strength site) (+ 0.1 (:production site)))
                                         num-sites-to-keep (if (> num-sites-to-keep 10) infinity num-sites-to-keep)]
                                     (update-in gm [:location-lookup {:x (:x site) :y (:y site)} :best-sites]
                                                #(take num-sites-to-keep
                                                       (sort (compare-by :score asc)
                                                            (conj % {:x (:x next-enemy-target)
                                                                     :y (:y next-enemy-target)
                                                                     :score (+ (:score next-enemy-target) (* 1.0 (:border-distance-exact site)))
                                                                     :border-distance-exact (:border-distance-exact site)}))))))
                                 game-map
                                 my-locations-visited)
            remaining-enemy-locations (if (:border-site next-enemy-target)
                                        (rest (remove :border-site enemy-locations))
                                        (rest enemy-locations))]
        (if (seq remaining-enemy-locations)
          (recur (inc iteration) new-game-map remaining-enemy-locations)
          new-game-map)))))

(defn-timed new-moves-setup
  "  * Iterate through all enemy sites sorted from best to worst, for each
      * Add border distances up to 7 sites - add two more properties :best-site and :best-score - if the :best-score is beaten replace it and replace the best-site
        * Note I think best-scores and best-targets would be better so that sites can go after their second best target if they don't need to go after the first.
      * Group all of my locations by the best site and call move-towards-target for each one passing in my locations as the group of best targets
      * Remove all locations which have moved, remove the enemy site from all my remaining locations"
  [game-map-with-borders game-map my-locations enemy-locations-in next-to-enemy? existing-moves num-owned num-players border-crossed frame ignore-profit?]
  (let [end-game (- (* 10 (Math/sqrt (* (:width game-map) (:height game-map)))) 1)]
    (if-let [max-sites (if (>= frame end-game) infinity (max-num-border-sites enemy-locations-in (or next-to-enemy? (:near-enemy game-map)) (:strongest-player game-map) num-owned (:total-strength game-map)))]
      (let [enemy-locations (if (>= frame end-game)
                              enemy-locations-in
                              (take max-sites (get-enemy-locations-worth-taking game-map-with-borders enemy-locations-in border-crossed ignore-profit? max-sites next-to-enemy?)))]
        (if (seq enemy-locations)
          (let [first-target (first enemy-locations)
                my-locations (remove #(already-moving? % existing-moves) my-locations)
                new-map (decorate-my-locations-with-best-sites game-map my-locations enemy-locations)]
            (loop [my-locations (filter #(seq (:best-sites %))
                                        (-> new-map :location-lookup vals))
                   enemy-locations enemy-locations
                   moves existing-moves
                   enemies-not-taken enemy-locations
                   iter 0]
              (let [next-enemy-target (assoc (first enemy-locations) :border-distance-exact 0)
                    new-game-map (assoc-in game-map [:location-lookup {:x (:x next-enemy-target)
                                                                       :y (:y next-enemy-target)}]
                                           next-enemy-target)
                    locations-to-target-this-enemy (filter #(let [best-site (first (:best-sites %))]
                                                              (and (= (:x next-enemy-target) (:x best-site))
                                                                   (= (:y next-enemy-target) (:y best-site))))
                                                           my-locations)
                    locations-to-target-this-enemy (map (fn [site]
                                                          (assoc site :border-distance-exact (-> site :best-sites first :border-distance-exact)))
                                                        locations-to-target-this-enemy)
                    number-to-visit (apply max 0 (map #(or (:border-distance-exact %) 0)
                                                      locations-to-target-this-enemy))
                    {new-game-map :game-map} (visit-neighbors-tracking-locations new-game-map next-enemy-target directions number-to-visit [])
                    extra-strength (if (:border-site next-enemy-target) 95 0)
                    extra-units 0
                    [all-moves new-moves taken? production-used]
                    (get-moves-for-target new-game-map next-enemy-target locations-to-target-this-enemy
                                          moves number-to-visit extra-strength extra-units)

                    new-moves (reduce (fn [moves new-move]
                                        (update moves :source-lookup assoc {:x (:x (:loc new-move))
                                                                            :y (:y (:loc new-move))}
                                                new-move))
                                      nil
                                      new-moves)
                    remaining-locations (->> my-locations
                                             (remove #(already-moving? % new-moves))
                                             (map (fn [site]
                                                    (update site :best-sites
                                                            (fn [old-sites]
                                                              (remove #(and (= (:x next-enemy-target) (:x %))
                                                                            (= (:y next-enemy-target) (:y %)))
                                                                      old-sites))))))

                    remaining-enemy-locations (if (:border-site next-enemy-target)
                                                ; (rest (remove :border-site enemy-locations))
                                                (rest enemy-locations)
                                                (rest enemy-locations))
                    enemies-not-taken (if taken?
                                        (remove #(and (= (:x next-enemy-target) (:x %))
                                                      (= (:y next-enemy-target) (:y %)))
                                                enemies-not-taken)
                                        enemies-not-taken)]

                (if (and (seq remaining-locations) (seq remaining-enemy-locations))
                  (recur remaining-locations remaining-enemy-locations all-moves enemies-not-taken (inc iter))
                  {:moves all-moves :targets enemies-not-taken}))))
          {:moves existing-moves}))
      {:moves existing-moves})))

(defn front-line-swap
  "Swap places with a weaker border 0 unit and a stronger border 1 unit. Returns a sequence of moves."
  [game-map site moves]
  (let [distance (:border-distance-exact site)
        strongest-site (->> (get-surrounding-sites game-map site)
                            (filter is-mine?)
                            (filter #(= (inc distance) (get % :border-distance-exact infinity)))
                            (remove #(already-moving? % moves))
                            (sort (compare-by :strength desc))
                            first)]
    (when (and strongest-site (> (:strength strongest-site) (* 3 (:strength site))))
      (if (seq (for [zero-strength-border (filter is-enemy? (get-surrounding-sites game-map site))
                     the-one-that-matters (filter #(and (is-enemy? %) (> (:strength %) 20)) (get-surrounding-sites game-map zero-strength-border))]
                 1))
        (if (<= (+ (:strength strongest-site) (:strength site) (:production site)) 255)
          [{:loc strongest-site :dir (get-direction-to-site strongest-site site)}
           {:loc site :dir :still}]
          [{:loc strongest-site :dir (get-direction-to-site strongest-site site)}
           {:loc site :dir (get-direction-to-site site strongest-site)}])
        [{:loc strongest-site :dir (get-direction-to-site strongest-site site)}
         {:loc site :dir (:direction (get-attack-direction game-map site moves infinity true false))}]))))
