(ns halite.map-basics
  (:require
   [halite.dsl :refer :all]))

(def directions
  "All directions"
  [:north :south :east :west])

(def opposite-direction
  {:north :south
   :south :north
   :east :west
   :west :east})

(def my-id (atom nil))

(defn set-id
  [id]
  (reset! my-id id))

(defn is-mine?
  [{:keys [owner]}]
  (= owner @my-id))

(defn is-neutral?
  [{:keys [owner strength]}]
  (and (= 0 owner) (> strength 0)))

(defn is-enemy?
  [site]
  (and (not (is-mine? site))
       (not (is-neutral? site))))

(defn get-site
  "Returns a site from the given site and direction"
  [game-map site direction]
  (let [{:keys [x y]} site
        site-x (case direction
                     :east (if (= (dec (:width game-map)) x) 0 (inc x))
                     :west (if (= 0 x) (dec (:width game-map)) (dec x))
                     x)
        site-y (case direction
                     :north (if (= 0 y) (dec (:height game-map)) (dec y))
                     :south (if (= (dec (:height game-map)) y) 0 (inc y))
                     y)]
    (get (:location-lookup game-map) {:x site-x :y site-y})))

(defn get-surrounding-sites
  "Returns a collection of sites surrounding the given site."
  [game-map site]
  (map #(get-site game-map site %) directions))

(defn get-num-players
  "Returns the number of players in the game."
  [sites]
  (-> (map :owner sites)
      distinct
      count
      dec))

(defn build-location-lookup
  "Builds the location lookup for fast performance."
  [sites]
  (into {}
        (for [site sites]
          [{:x (:x site) :y (:y site)} site])))

(defn add-location-lookup
  "Adds the location lookup to the game-map"
  [game-map]
  (assoc game-map :location-lookup (build-location-lookup (:sites game-map))))

(defn any-enemies?
  "Returns whether any of the provided locations are owned by an enemy (and not neutral). We
  consider a strength of 0 to be an enemy."
  [sites]
  (some is-enemy? sites))

(defn next-to-any-enemies?
  "Returns true if the provided site is next to any enemies that we may want to take."
  [game-map site]
  (->> (get-surrounding-sites game-map site)
       (remove is-mine?)
       (some #(not (and (= (:production %) 0) (> (:strength %) 0))))))

(defn crossing-over
  "Returns true if the move involves crossing over from one side of the map to the other."
  [from to]
  (> (Math/abs (- from to)) 1))

(def get-direction-to-site
  "Returns the direction to travel to get from one site to an adjacent site."
  (memoize
   (fn [from-site to-site]
    ;  (if (and (= (:x from-site) (:x to-site)) (= (:y from-site) (:y to-site)))
    ;    :still
     (if (= (:x from-site) (:x to-site))
       (if (crossing-over (:y from-site) (:y to-site))
         (if (= 0 (:y from-site))
           :north
           :south)
         (if (> (:y from-site) (:y to-site))
           :north
           :south))
       (if (crossing-over (:x from-site) (:x to-site))
         (if (= 0 (:x from-site))
           :west
           :east)
         (if (> (:x from-site) (:x to-site))
           :west
           :east))))))

(defn get-reachable-enemy-locations
  "Returns all of the enemy locations that I can directly attack."
  [game-map]
  (into #{}
    (for [site (vals (:location-lookup game-map))
          :when (is-mine? site)
          :let [surrounding-sites (get-surrounding-sites game-map site)]
          surrounding-site surrounding-sites
          :when (and (not (is-mine? surrounding-site)))]
                     ;(or (is-enemy? surrounding-site
                      ;                                     (> (:production surrounding-site) 0))))]
      surrounding-site)))

(defn-timed build-memoized-lookup
  "Build up the memoized lookup."
  [game-map]
  (let [game-map (add-location-lookup game-map)]
    (doseq [site (vals (:location-lookup game-map))
            :let [surrounding-sites (get-surrounding-sites game-map site)]]
      (mapv #(get-direction-to-site site %) surrounding-sites))))

(defn get-distance-exact
  "Returns the number of rounds to move from one location to another location"
  [game-map loc1 loc2]
  (let [{:keys [width height]} game-map
        dx (Math/abs (- (:x loc1) (:x loc2)))
        dy (Math/abs (- (:y loc1) (:y loc2)))
        dx (if (> dx (/ width 2))
             (- width dx)
             dx)
        dy (if (> dy (/ height 2))
             (- height dy)
             dy)]
    (+ dx dy)))
