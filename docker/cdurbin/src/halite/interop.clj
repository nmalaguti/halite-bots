(ns halite.interop
  (:import (io.halite GameMap
                      Networking
                      Move
                      Site
                      InitPackage
                      Location
                      Direction))
  (:require [clojure.java.data :refer :all]))

;; to and from java methods

(defmethod from-java Location
  [instance]
  {:x (.-x instance)
   :y (.-y instance)})

(defmethod to-java [Location clojure.lang.APersistentMap]
  [clazz props]
  (Location. (:x props) (:y props)))

(defmethod from-java Move
  [instance]
  {:loc (from-java (.-loc instance))
   :dir (from-java (.-dir instance))})

(defmethod to-java [Move clojure.lang.APersistentMap]
  [clazz props]
  (Move. (to-java Location (:loc props))
         (to-java Direction (:dir props))))

(defmethod to-java [Direction clojure.lang.Keyword]
  [clazz keyword]
  (Direction/valueOf (.toUpperCase (name keyword))))

(defmethod from-java Site
  [instance]
  {:owner (.-owner instance)
   :strength (.-strength instance)
   :production (.-production instance)})

(defmethod from-java InitPackage
  [instance]
  {:id (.-myID instance)
   :game-map (from-java (.-map instance))})

;; Note that the sites include x and y keys
(defmethod from-java GameMap
  [instance]
  (let [width (.-width instance)
        height (.-height instance)]
    {:width width
     :height height
     :sites (map
                (fn [[x y]]
                  (let [loc {:x x :y y}]
                    (merge
                     loc
                     (from-java (.getSite instance (to-java Location loc))))))
                (for [y (range 0 height)
                      x (range 0 width)]
                  [x y]))}))

;; helpers

(defn get-init [] (from-java (Networking/getInit)))

(defn send-init [bot-name] (Networking/sendInit bot-name))

(defn get-frame [] (from-java (Networking/getFrame)))

(defn send-moves
  [moves]
  (Networking/sendFrame
    (java.util.ArrayList. (map #(to-java Move %) moves))))
