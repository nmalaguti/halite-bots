package com.nmalaguti.halite

import com.nmalaguti.halite.Direction
import com.nmalaguti.halite.GameMap
import com.nmalaguti.halite.Location
import com.nmalaguti.halite.MINIMUM_STRENGTH
import com.nmalaguti.halite.Move
import com.nmalaguti.halite.PI4
import com.nmalaguti.halite.Site
import com.nmalaguti.halite.Stats
import kotlin.comparisons.compareBy

object MyBot {
    lateinit var gameMap: GameMap
    lateinit var nextMap: GameMap
    var id: Int = 0
    var turn: Int = 0
    var allMoves = mutableSetOf<Move>()
    var start = System.currentTimeMillis()
    var startInit = System.currentTimeMillis()
    var lastTurnMoves: Map<Location, Move> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()
    var movedLocations = setOf<Location>()
    var innerBorderCells: List<Location> = listOf()

    @Throws(java.io.IOException::class)
    @JvmStatic fun main(args: Array<String>) {
        initializeLogging()
        init()

        // game loop
        while (true) {
            // get frame
            gameMap = Networking.getFrame()
            nextMap = GameMap(gameMap)

            start = System.currentTimeMillis()
            logger.info("===== Turn: ${turn++} at $start =====")
            logger.info("stats: ${playerStats[id]}")

            lastTurnMoves = allMoves.associateBy { it.loc }
            playerStats = playerStats()

            // reset all moves
            allMoves = mutableSetOf()

            // make moves based on value
            allMoves.addAll(makeValueMoves())

            removeUnwiseMoves()
            updateMovedIndex()

            innerBorderCells = gameMap.filter { it.isInnerBorder() }

            // make joint moves
            allMoves.addAll(makeJointMoves())

            removeUnwiseMoves()
            updateMovedIndex()

            // make moves that abandon cells that will take too long to conquer
            allMoves.addAll(makeAbandonMoves())

            removeUnwiseMoves()
            updateMovedIndex()

            // find a friendly unit and help out
            allMoves.addAll(makeAssistMoves())

            endGameLoop()

            Networking.sendFrame(allMoves)
        }
    }

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID

        logger.info("id: $id")

        Networking.sendInit(BOT_NAME)
    }

    fun endGameLoop() {
        removeUnwiseMoves()

        // deal with combining cells to strength over 255
        val newMap = simulateNextFrame(allMoves, gameMap)
        val wastage = permutations(newMap).filter { newMap.getSite(it).owner == id && newMap.getSite(it).strength > 255 }.toSet()

        wastage.forEach {
            logger.info("wastage of ${newMap.getSite(it).strength} at $it")
        }

        val movesByDest = allMoves
                .groupBy { it.loc.move(it.dir) }

        wastage.filter { newMap.getSite(it).strength > 300 }.forEach { loc ->
            if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

            // is destination staying still and causing problems?
            // - either production + strength > 255
            // are too many pieces moving there
            val incoming = movesByDest[loc]
            val site = newMap.getSite(loc)
            if (incoming != null) {
                val possibleDirections = Direction.CARDINALS.filter { newMap.getSite(loc, it).strength + site.strength <= 255 }
                val incomingStrength = incoming.sumBy { it.loc.site().strength }
                if (loc !in movedLocations && incomingStrength <= 255 && possibleDirections.isNotEmpty()) {
                    // move out of the way
                    allMoves.add(Move(loc, possibleDirections.first()))
                } else if (loc !in movedLocations && incomingStrength <= 255) {
                    // can't move out of the way
                    // can we send in less strength?
                    if (incoming.any { site.strength + it.loc.site().strength <= 255 }) {
                        var left = incoming.sumBy { it.loc.site().strength } + site.strength
                        val sorted = incoming.sortedBy { it.loc.site().strength }.toMutableList()
                        val toChange = mutableListOf<Move>()
                        while (left > 255 && sorted.isNotEmpty()) {
                            val next = sorted.removeAt(0)
                            left -= next.loc.site().strength
                            toChange.add(next)
                        }

                        // can we change some of these moves?
                        toChange.forEach { changeMe ->
                            allMoves.remove(changeMe)

                            val morePossibleDirections = Direction.CARDINALS
                                    .filter { newMap.getSite(changeMe.loc, it).strength + changeMe.loc.site().strength <= 255 }

                            if (morePossibleDirections.isNotEmpty()) {
                                allMoves.add(Move(changeMe.loc, morePossibleDirections.first()))
                            } else {
                                allMoves.add(Move(changeMe.loc, Direction.STILL))
                            }
                        }
                    } else {
                        // nope - all or nothing
                        incoming.forEach { changeMe ->
                            allMoves.remove(changeMe)

                            val morePossibleDirections = Direction.CARDINALS
                                    .filter { newMap.getSite(changeMe.loc, it).strength + changeMe.loc.site().strength <= 255 }

                            if (morePossibleDirections.isNotEmpty()) {
                                allMoves.add(Move(changeMe.loc, morePossibleDirections.first()))
                            } else {
                                allMoves.add(Move(changeMe.loc, Direction.STILL))
                            }
                        }
                    }
                } else {
                    var left = incoming.sumBy { it.loc.site().strength }
                    val sorted = incoming.sortedBy { it.loc.site().strength }.toMutableList()
                    val toChange = mutableListOf<Move>()
                    while (left > 255 && sorted.isNotEmpty()) {
                        val next = sorted.removeAt(0)
                        left -= next.loc.site().strength
                        toChange.add(next)
                    }

                    // can we change some of these moves?
                    toChange.forEach { changeMe ->
                        allMoves.remove(changeMe)

                        val morePossibleDirections = Direction.CARDINALS
                                .filter { newMap.getSite(changeMe.loc, it).strength + changeMe.loc.site().strength <= 255 }

                        if (morePossibleDirections.isNotEmpty()) {
                            allMoves.add(Move(changeMe.loc, morePossibleDirections.first()))
                        } else {
                            allMoves.add(Move(changeMe.loc, Direction.STILL))
                        }
                    }
                }
            } else {
                // staying still is causing it to go over
//                val possibleDirections = Direction.CARDINALS
//                        .sortedBy { newMap.getSite(loc, it).strength + loc.site().strength <= 255 }
//                allMoves.add(Move(loc, possibleDirections.first()))
            }
        }

        val newNewMap = simulateNextFrame(allMoves, gameMap)
        val newWastage = permutations(newMap).filter { newNewMap.getSite(it).owner == id && newNewMap.getSite(it).strength > 255 }.toSet()

        newWastage.forEach {
            logger.info("still wastage of ${newNewMap.getSite(it).strength} at $it")
        }
    }

    fun removeUnwiseMoves() {
        if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

        // audit all moves to prevent repeated swapping
        allMoves.removeAll {
            val moveFromDestination = lastTurnMoves[it.loc.move(it.dir)]
            moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == it.loc
        }

        // remove moves that attack other players with too little strength
        allMoves.removeAll {
            val destination = it.loc.move(it.dir)

            destination.site().isEnvironment() && destination.site().strength == 0 &&
                    it.loc.site().strength <  Math.min(it.loc.site().production * 2, MINIMUM_STRENGTH)
        }
    }

    fun updateMovedIndex() {
        movedLocations = allMoves.map { it.loc }.toSet()
    }

    // MOVE LOGIC

    fun makeValueMoves(): List<Move> {
        if (System.currentTimeMillis() - start > MAXIMUM_TIME) return listOf()

        // select a move for each point based on site value
        val moves = gameMap
                .filter { it.site().isMine() && it.site().strength > 0 }
                .map { loc ->
                    val site = loc.site()
                    if (loc.isInnerBorder()) {
                        val targets = loc.enemies().filter { site.strength > it.loc.site().strength }

                        if (targets.isNotEmpty()) {
                            val best = targets.sortedBy { it.loc.value(it.origin) }.first()
                            Move(best.origin, best.direction)
                        } else null
                    } else if (loc.site().strength > Math.min(loc.site().production * 4, MINIMUM_STRENGTH * 3 + 1)
                            && loc !in lastTurnMoves) {
                        val best = loc.allNeighborsWithin(7)
                                .filterNot { it.site().isMine() }
                                .sortedBy { it.value(loc) }
                                .firstOrNull()

                        if (best != null) {
                            moveTowards(loc, best)
                        } else {
                            Move(loc, loc.straightClosestEdge())
                        }
                    } else null
                }
                .filterNotNull()

        return moves
    }

    fun makeJointMoves(): List<Move> {
        if (System.currentTimeMillis() - start > MAXIMUM_TIME) return listOf()

        // look for opportunities to combine strength
        val moves = innerBorderCells
                .filterNot { it in movedLocations }
                .filter { it.site().strength > 0 }
                .map { it.bestTarget() }
                .filterNotNull()
                .groupBy { it.loc }
                .filter { it.value.sumBy { it.origin.site().strength } > it.key.site().strength }
                .flatMap { it.value.map { Move(it.origin, it.direction) } }

        return moves
    }

    fun makeAbandonMoves(): List<Move> {
        if (System.currentTimeMillis() - start > MAXIMUM_TIME) return listOf()

        // abandon cells that will take too long to conquer
        val moves = innerBorderCells
                .filterNot { it in movedLocations }
                .filter { it.site().strength > MINIMUM_STRENGTH }
                .map { it.enemies() }
                .filter { it.isNotEmpty() }
                .map {
                    val sorted = it.sortedBy { it.loc.value(it.origin) }
                    val best = sorted.first()
                    val numberOfTurns =
                            (best.loc.site().strength - best.origin.site().strength) / best.origin.site().production.toDouble()

                    if (numberOfTurns > 5) {
                        sorted.find { it.origin.site().strength > it.loc.site().strength }
                    } else null
                }
                .filterNotNull()
                .map { Move(it.origin, it.direction) }

        return moves
    }

    fun makeAssistMoves(): List<Move> {
        if (System.currentTimeMillis() - start > MAXIMUM_TIME) return listOf()

        // find a friendly unit and help out
        val moves = innerBorderCells
                .filterNot { it in movedLocations }
                .filter { it.site().strength > MINIMUM_STRENGTH }
                .map { self ->
                    val bestTarget = self.bestTarget()

                    self.friends()
                            .filter { it.loc.isInnerBorder() && it.loc !in movedLocations }
                            .map { friend ->
                                val friendBestTarget = friend.loc.bestTarget()

                                if (bestTarget != null && friendBestTarget != null &&
                                        // lower values are better
                                        friendBestTarget.loc.value(friendBestTarget.origin) < bestTarget.loc.value(bestTarget.origin) &&
                                        // our combined strength can take the target
                                        friend.loc.site().strength + self.site().strength > friendBestTarget.loc.site().strength) {
                                    friend to friendBestTarget
                                } else null
                            }
                            .filterNotNull()
                            .sortedBy { it.second.loc.value(it.second.origin) }
                            .map { it.first }
                            .firstOrNull()
                }
                .filterNotNull()
                .map { Move(it.origin, it.direction) }

        return moves
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isMine() = this.owner == id

    fun Location.value(origin: Location): Double {
        if (this.site().isEnvironment() && this.site().strength > 0) {
            return (this.site().strength + origin.site().production) / Math.pow(this.site().production.toDouble(), 2.0)
        } else {
            // overkill
            val strength = origin.site().strength
            val damage = this.enemies()
                    .filter { it.loc.site().isOtherPlayer() }
                    .map {
                        Math.min(strength, it.loc.site().strength) + it.loc.site().production
                    }.sum() +
                    Math.min(this.site().strength, strength) + this.site().production


            return -damage.toDouble() - strength
        }
    }

    // LOCATION

    fun Location.isInnerBorder() =
            this.site().isMine() &&
                    this.neighbors().any { !it.loc.site().isMine() }

    fun Location.isOuterBorder() =
            !this.site().isMine() &&
                    this.neighbors().any { it.loc.site().isMine() }

    fun Location.neighbors() = Direction.CARDINALS.map { RelativeLocation(this, it, this.move(it)) }

    fun Location.enemies() = this.neighbors().filterNot { it.loc.site().isMine() }

    fun Location.friends() = this.neighbors().filter { it.loc.site().isMine() }

    fun Location.bestTarget(): RelativeLocation? = this.enemies().sortedBy { it.loc.value(it.origin) }.firstOrNull()

    fun Location.move(direction: Direction) = gameMap.getLocation(this, direction)

    fun Location.site() = gameMap.getSite(this)

    fun Location.straightClosestEdge(): Direction {
        val maxDistance = Math.min(gameMap.width, gameMap.height) / 2

        return Direction.CARDINALS.map {
            var loc = this
            var distance = 0

            while (loc.site().isMine() && distance < maxDistance) {
                loc = loc.move(it)
                distance++
            }

            it to distance
        }.sortedBy { it.second }.first().first
    }

    fun Location.allNeighborsWithin(distance: Int) = gameMap.filter { gameMap.getDistance(it, this) < distance }

    // GAMEMAP

    fun playerStats() = gameMap
            .map { it.site() }
            .groupBy { it.owner }
            .mapValues { Stats(it.value.size, it.value.sumBy { it.production }, it.value.sumBy { it.strength }) }

    // CLASSES

    class RelativeLocation(val origin: Location, val direction: Direction, val loc: Location)

    // HELPER METHODS

    fun permutations(first: IntRange, second: IntRange) =
            first.flatMap { y ->
                second.map { x ->
                    Location(x, y)
                }
            }

    fun permutations(gameMap: GameMap) = permutations(0 until gameMap.height, 0 until gameMap.width)

    fun moveTowards(start: Location, end: Location): Move {
        val angle = gameMap.getAngle(start, end)
        return if (angle >= -PI4 && angle <= PI4) {
            Move(start, Direction.WEST)
        } else if (angle >= PI4 && angle <= 3 * PI4) {
            Move(start, Direction.SOUTH)
        } else if (angle >= 3 * PI4 || angle <= 3 * -PI4) {
            Move(start, Direction.EAST)
        } else { // if (angle >= 3 * -pi4 && angle <= -pi4)
            Move(start, Direction.NORTH)
        }
    }

    fun simulateNextFrame(moves: Collection<Move>, map: GameMap): GameMap {
        val newMap = GameMap(map)

        val toUpdate = permutations(newMap).filter { newMap.getSite(it).owner == id }.toMutableSet()

        moves.forEach {
            if (it.loc in toUpdate) {
                toUpdate.remove(it.loc)
                val originSite = map.getSite(it.loc)
                val startSite = newMap.getSite(it.loc)
                val destinationSite = newMap.getSite(it.loc, it.dir)

                if (startSite.owner == destinationSite.owner) {
                    if (it.dir == Direction.STILL) {
                        destinationSite.strength += destinationSite.production
                    } else {
                        startSite.strength -= originSite.strength
                        destinationSite.strength += originSite.strength
                    }
                } else {
                    startSite.strength -= originSite.strength
                    if (destinationSite.strength <= originSite.strength) {
                        destinationSite.strength = originSite.strength - destinationSite.strength
                        destinationSite.owner = originSite.owner
                    } else {
                        destinationSite.strength -= originSite.strength
                    }
                }
            }
        }

        toUpdate.forEach {
            val destinationSite = newMap.getSite(it)
            destinationSite.strength += destinationSite.production
        }

        return newMap
    }
}
