(ns halite.map-analysis
  (:require
   [halite.dsl :refer :all]
   [halite.map-basics :refer :all]
   [halite.borders :refer :all]
   [halite.constants :refer :all]))

(defn get-profit-for-site
  "Returns the profit for a site"
  [{:keys [strength production]}]
  (cond
    (and (= 0 strength) (= 0 production)) 1
    (= 0 production) infinity
    :else (/ strength production)))

(defn add-profit
  "Adds profit info to sites"
  [sites]
  (map (fn [site]
         (let [profit (get-profit-for-site site)]
           (assoc site :profit profit :score profit)))
       sites))

(defn get-turns-to-take
  "Returns the number of turns to take the provided site."
  [{:keys [strength production] :as site} total-strength total-production]
  (Math/ceil (max 0 (/ (- strength total-strength) (+ 0.001 total-production)))))

(defn get-player-stats-from-sites
  "Returns player stats."
  [game-map]
  (into {}
    (map (fn [[owner tiles]]
           [owner {:total-strength (reduce + (map :strength tiles))
                   :total-production (reduce + (map :production tiles))
                   :total-territory (count tiles)
                   :open-borders (when (not= 0 owner)
                                   (reduce (fn [count tile]
                                             (if (seq (filter #(and (or (and (= 0 (:owner %))
                                                                             (= 0 (:strength %)))
                                                                        (not= 0 (:owner %)))
                                                                    (not= (:owner %) (:owner tile)))
                                                              (get-surrounding-sites game-map tile)))
                                                (inc count)
                                                count))
                                           0
                                           tiles))}])
         (group-by :owner (:sites game-map)))))

(defn strongest-player?
  "Returns true if I am the strongest player."
  [player-stats]
  (let [my-strength (get-in player-stats [@my-id :total-strength])]
    (> (* 0.75 my-strength) (apply max 0 (map :total-strength (vals (dissoc player-stats 0 @my-id)))))))

(defn get-owners-to-attack
  "Returns which owners I should target."
  [player-stats enemies-with-borders]
  (let [my-strength (get-in player-stats [@my-id :total-strength])]
    (for [owner (keys player-stats)
          :let [enemy-stats (get player-stats owner)]
          :when (and (not= 0 owner) (not= @my-id owner)
                     (or (> my-strength (* 2 (:total-strength enemy-stats)))))]
      owner)))

(defn predict-relevant-score-tunnel
  "Heuristic to try to determine the future benefit from taking a particular cell. Change from the
  java code to not ignore enemy sites. Also better at ignoring sites already visited."
  [game-map field site num-rounds sites-to-ignore production-gained strength-used]
  (if (is-mine? site)
    infinity
    (if (>= 0 num-rounds)
      (let [denominator (+ 0.0001 production-gained)]
        (/ strength-used denominator))
      ;; Recursively calculate the score
      (let [surrounding-sites (get-surrounding-sites game-map site)
            min-score
                      (apply min
                             (for [surr-site surrounding-sites]
                               (if (and (= 0 (:owner surr-site)) (not (some #{surr-site} sites-to-ignore)))
                                 (let [production-gained (+ (:production surr-site) production-gained)
                                       strength-used (+ (:strength surr-site) strength-used)]
                                    (predict-relevant-score-tunnel game-map field surr-site (dec num-rounds)
                                                                   (conj sites-to-ignore surr-site)
                                                                   production-gained
                                                                   strength-used))
                                 infinity)))]
        (if (>= min-score infinity)
          (let [denominator (+ 0.0001 production-gained)]
            (/ strength-used denominator))
          min-score)))))

(defn predict-relevant-score-tunnel-with-turns-to-take
  "Heuristic to try to determine the future benefit from taking a particular cell. Change from the
  java code to not ignore enemy sites. Also better at ignoring sites already visited."
  [game-map field site num-rounds sites-to-ignore total-strength total-production turns-taken production-gained strength-used]
  (if (is-mine? site)
    infinity
    (if (>= 0 num-rounds)
      (let [denominator (+ 0.0001 production-gained)]
        (+ (* 2 (/ turns-taken denominator)) (/ strength-used denominator)))
       ; (+ turns-taken (/ strength-used denominator)))
      ;; Recursively calculate the score
      (let [surrounding-sites (get-surrounding-sites game-map site)
            min-score
                      (apply min
                             (for [surr-site surrounding-sites]
                               (if (and (= 0 (:owner surr-site)) (not (some #{surr-site} sites-to-ignore)))
                                 (let [turns-taken (+ (inc turns-taken) (get-turns-to-take surr-site total-strength total-production))
                                       production-gained (+ (:production surr-site) production-gained)
                                       strength-used (+ (:strength surr-site) strength-used)]
                                    (predict-relevant-score-tunnel-with-turns-to-take game-map field surr-site (dec num-rounds)
                                                                                      (conj sites-to-ignore surr-site)
                                                                                      (max 0 (- total-strength (:strength surr-site)))
                                                                                      (+ total-production (:production surr-site))
                                                                                      turns-taken
                                                                                      production-gained
                                                                                      strength-used))
                                 infinity)))]
        (if (>= min-score infinity)
          (let [denominator (+ 0.0001 production-gained)]
            ; (+ turns-taken (/ strength-used denominator))
            (+ (* 2 (/ turns-taken denominator)) (/ strength-used denominator)))
          min-score)))))

(defn add-score-tunnel
  "Adds the score key to the from-site with the estimated score by taking this site (lower is better) using a best site calculation."
  [enemy-targets total-strength total-production game-map field site num-rounds]
  (assoc site :score (predict-relevant-score-tunnel game-map field site num-rounds enemy-targets (:production site) (:strength site))))

(defn add-score-tunnel-with-turns-to-take
  "Adds the score key to the from-site with the estimated score by taking this site (lower is better) using a best site calculation."
  [enemy-targets total-strength total-production game-map field site num-rounds]
  (let [production-with-site (+ total-production (:production site))
        turns-taken (if (> total-strength 355)
                      1
                      (get-turns-to-take site total-strength total-production))
        production-gained (:production site)]
    (assoc site :score (predict-relevant-score-tunnel-with-turns-to-take game-map field site num-rounds enemy-targets (- total-strength (:strength site)) production-with-site turns-taken production-gained (:strength site)))))

(defn add-enemy-stats
  "Returns the number of enemies and total production on ties."
  [game-map site from-site]
  (let [my-strength (:strength from-site)
        ss (get-surrounding-sites game-map site)
        enemies (filter is-enemy? ss)
        neutrals (filter is-neutral? ss)
        mine (filter is-mine? ss)]
    (assoc site :ecount (count enemies)
           :neutral-count (count neutrals)
           :my-count (count mine)
           :total-production (reduce + (:production site) (map :production enemies))
           :total-strength (reduce + (min (:strength site) my-strength) (map #(min my-strength (:strength %)) enemies)))))

(defn get-num-rounds-for-scoring
  "Returns the number of sites to visit when determining the score for a site"
  [game-map num-owned frame]
  (let [width (get game-map :width 30)
        height (get game-map :height 30)
        num-players (:num-players game-map)
        ;; Don't need to calculate this every round - TODO performance improvement with an atom set at init
        max-num-to-visit (inc (Math/ceil (* (/ width num-players 2)
                                            (/ height num-players 2))))
        num-based-on-num-owned (cond
                                 (= 0 frame) 8
                                 (= num-owned 1) 7
                                 (< num-owned max-early-round-targets) 6
                                 (< num-owned 40) 6
                                 (< num-owned 100) 5
                                 (< num-owned 500) 4
                                 :else 3)]
    (min max-num-to-visit num-based-on-num-owned)))


(defn-timed build-tracking-for-early-round
  "Adds all the info to the game-map to make things faster."
  [game-map num-owned starting-time frame]
  (let [uh-oh-time (if (= 0 frame) 5000 675)
        num-players (get-num-players (:sites game-map))
        player-stats (get-player-stats-from-sites game-map)
        total-strength (get-in player-stats [@my-id :total-strength] 100)
        average-strength (/ total-strength num-owned)
        total-production (get-in player-stats [@my-id :total-production] 0.01)
        profit-fn #(update % :sites add-profit)
        game-map (-> game-map
                     profit-fn
                     (assoc :num-players num-players)
                     add-location-lookup)
        enemy-targets (get-reachable-enemy-locations game-map)
        next-to-enemy? (any-enemies? enemy-targets)
        enemies-with-borders (when next-to-enemy?
                               (set (flatten
                                      (keep (fn [target]
                                              (when (is-enemy? target)
                                                (map :owner (get-surrounding-sites game-map target))))
                                            enemy-targets))))
        ; _ (log! (str "Enemies with borders" enemies-with-borders))
        owners-to-attack (set (get-owners-to-attack player-stats enemies-with-borders))

        ; _ (log! (str "owners to attack" (pr-str owners-to-attack)))
        num-rounds (get-num-rounds-for-scoring game-map num-owned frame)
        score-fn (if (< num-owned max-early-round-targets)
                  (partial add-score-tunnel-with-turns-to-take enemy-targets total-strength total-production)
                  (partial add-score-tunnel enemy-targets total-strength total-production))
        should-attack (or (not next-to-enemy?)
                          (strongest-player? player-stats))
        end-game (- (* 10 (Math/sqrt (* (:width game-map) (:height game-map)))) 1)
        game-map (reduce (fn [current-game-map site]
                           (if (< (- (System/currentTimeMillis) starting-time) uh-oh-time)
                             (let [site (score-fn current-game-map :profit site num-rounds)
                                   border-site (when (and (not= 2 num-players) (< frame end-game))
                                                 (unbroken-border-site? current-game-map site owners-to-attack))
                                   avoid-location? (when (< frame end-game) (avoid-location? current-game-map site next-to-enemy? false owners-to-attack))]
                               (assoc-in current-game-map [:location-lookup {:x (:x site) :y (:y site)}]
                                         (assoc site :avoid avoid-location? :border-site border-site)))
                             (reduced current-game-map)))
                         (assoc game-map :enemies-with-borders enemies-with-borders)
                         enemy-targets)]
    (assoc game-map :next-to-enemy next-to-enemy? :total-production total-production :total-strength total-strength :average-strength average-strength :strongest-player should-attack :near-enemy (seq (filter :avoid (vals (:location-lookup game-map)))))))

(defn site-strength-over-255?
  "Returns true if I am moving to this site and will have over 255 strength."
  [moves site strength]
  (< 255 (apply + strength
                (->> (get (:dest-lookup moves) {:x (:x site) :y (:y site)}
                          [{:loc {:strength 0}}])
                     (map :loc)
                     (map :strength)))))
