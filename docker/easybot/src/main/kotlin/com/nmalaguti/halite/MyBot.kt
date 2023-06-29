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
    var distanceToEnemyGrid = mutableListOf<MutableList<Int>>()

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

            buildDistanceToEnemyGrid()

            makeBattleMoves()

            removeUnwiseMoves()

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

    fun removeUnwiseMoves() {
        // audit all moves to prevent repeated swapping
        allMoves.removeAll {
            val moveFromDestination = lastTurnMoves[it.loc.move(it.dir)]
            moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == it.loc
        }
    }

    // MOVE LOGIC

    fun walkGridFrom(openSet: MutableSet<Location>, closedSet: MutableSet<Location>) {
        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isMine()) {
                    distanceToEnemyGrid[current.y][current.x] =
                            Math.min(
                                    distanceToEnemyGrid[current.y][current.x],
                                    2 + current.neighbors().map { distanceToEnemyGrid[it.loc.y][it.loc.x] }.min()!!
                            )
                }

                current.neighbors()
                        .filterNot { it.loc.site().isEnvironment() && it.loc.site().strength > 0 }
                        .forEach { openSet.add(it.loc) }
            }
        }
    }

    fun buildDistanceToEnemyGrid() {
        distanceToEnemyGrid = mutableListOf<MutableList<Int>>()
        for (y in 0 until gameMap.height) {
            val row = mutableListOf<Int>()
            for (x in 0 until gameMap.width) {
                row.add(Location(x, y).site().resource())
            }
            distanceToEnemyGrid.add(row)
        }

        gameMap.filter { it.isOuterBorder() }.sortedBy { distanceToEnemyGrid[it.y][it.x] }.take(20).forEach {
            walkGridFrom(mutableSetOf(it), mutableSetOf())
        }

        logDistanceToEnemyGrid()
    }

    fun logDistanceToEnemyGrid() {
        val builder = StringBuilder("\n")
        builder.append("     ")
        builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
        builder.append("\n")
        for (y in 0 until gameMap.height) {
            builder.append("$y".take(3).padEnd(4) + " ")
            builder.append(distanceToEnemyGrid[y].map { "$it".take(3).padEnd(4) }.joinToString(" "))
            builder.append(" " + "$y".take(3).padEnd(4))
            builder.append("\n")
        }
        builder.append("     ")
        builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
        builder.append("\n")

        logger.info(builder.toString())
    }

    fun makeBattleMoves() {
        gameMap
                .filter { it.site().isMine() }
                .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { it.neighbors().filterNot { it.loc.site().isMine() }.size }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    var target = loc.neighbors()
                            .filter { distanceToEnemyGrid[it.loc.y][it.loc.x] < distanceToEnemyGrid[loc.y][loc.x] }
                            .sortedByDescending { it.loc.overkill() }
                            .filter {
                                val nextSite = nextMap.getSite(it.loc)

                                if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                    // enemy space
                                    loc.site().strength > Math.max(loc.site().production * 3, MINIMUM_STRENGTH) &&
                                            nextSite.strength + loc.site().strength < 256 + MINIMUM_STRENGTH
                                } else if (nextSite.isEnvironment()) {
                                    // environment
                                    loc.site().strength > loc.site().production * 2 &&
                                            nextSite.strength < loc.site().strength
                                } else {
                                    // mine
                                    loc.site().strength > Math.max(loc.site().production * 3, MINIMUM_STRENGTH) &&
                                            nextSite.strength + loc.site().strength < 256 + MINIMUM_STRENGTH
                                }
                            }
                            .firstOrNull()?.loc

                    if (target == null) {
                        target = loc.neighbors()
                                .filter { distanceToEnemyGrid[it.loc.y][it.loc.x] < distanceToEnemyGrid[loc.y][loc.x] }
                                .sortedByDescending { it.loc.overkill() }
                                .map {
                                    val nextSite = nextMap.getSite(it.loc)

                                    if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                        // enemy space
                                        if (loc.site().strength <= Math.max(loc.site().production * 3, MINIMUM_STRENGTH)) {
                                            // wait more
                                            null
                                        } else if (nextSite.strength + loc.site().strength >= (256 + MINIMUM_STRENGTH)) {
                                            // flow problems...
                                            logger.info("flow problem at ${loc} -> ${it.loc}")
                                            loc.neighbors()
                                                    .filter {
                                                        if (nextMap.getSite(it.loc).isMine()) nextSite.strength + nextMap.getSite(it.loc).strength < (256 + MINIMUM_STRENGTH)
                                                        else true
                                                    }
                                                    .sortedByDescending { distanceToEnemyGrid[it.loc.y][it.loc.x] }
                                                    .firstOrNull()
//                                            null
                                        } else {
                                            // who knows?
                                            null
                                        }
                                    } else if (nextSite.isMine()) {
                                        // mine
                                        if (loc.site().strength <= Math.max(loc.site().production * 3, MINIMUM_STRENGTH)) {
                                            // wait more
                                            null
                                        } else if (nextSite.strength + loc.site().strength >= (256 + MINIMUM_STRENGTH)) {
                                            // flow problems...
                                            logger.info("flow problem at ${loc} -> ${it.loc}")
                                            loc.neighbors()
                                                    .filter {
                                                        if (nextMap.getSite(it.loc).isMine()) nextSite.strength + nextMap.getSite(it.loc).strength < (256 + MINIMUM_STRENGTH)
                                                        else true
                                                    }
                                                    .sortedByDescending { distanceToEnemyGrid[it.loc.y][it.loc.x] }
                                                    .firstOrNull()
//                                            null
                                        } else {
                                            // who knows?
                                            null
                                        }
                                    } else null
                                }
                                .filterNotNull()
                                .firstOrNull()?.loc
                    }

                    if (target != null) {
                        val move =
                                if (loc != target)  moveTowards(loc, target)
                                else Move(loc, Direction.STILL)

                        val originSite = gameMap.getSite(loc)
                        val startSite = nextMap.getSite(loc)
                        val destinationSite = nextMap.getSite(target)

                        if (startSite.owner == destinationSite.owner) {
                            if (move.dir == Direction.STILL) {
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

                        if (destinationSite.strength > 255) destinationSite.strength = 255

                        allMoves.add(move)
                    }
                }
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Location.isOtherPlayer() = this.site().isOtherPlayer()

    fun Site.isEnvironment() = this.owner == 0

    fun Location.isEnvironment() = this.site().isEnvironment()

    fun Site.isMine() = this.owner == id

    fun Location.isMine() = this.site().isMine()

    fun Location.overkill() = this.neighbors()
            .map {
                if (it.loc.isOtherPlayer()) it.loc.site().strength + it.loc.site().production
                else if (nextMap.getSite(it.loc).isMine()) it.loc.friends().map { -nextMap.getSite(it.loc).strength }.sum()
                else 0
            }.sum()

    fun Site.resource() = if (!this.isMine()) (this.strength / this.production.toDouble()).toInt() else 9999

    // LOCATION

    fun Location.isInnerBorder() =
            this.site().isMine() &&
                    this.neighbors().any { !it.loc.site().isMine() }

    fun Location.isOuterBorder() =
            !this.site().isMine() &&
                    this.neighbors().any { it.loc.site().isMine() }

    fun Location.neighbors() = Direction.CARDINALS.map { RelativeLocation(this, it, this.move(it)) }

    fun Location.neighborsAndSelf() = Direction.DIRECTIONS.map { RelativeLocation(this, it, this.move(it)) }

    fun Location.enemies() = this.neighbors().filterNot { it.loc.site().isMine() }

    fun Location.friends() = this.neighbors().filter { it.loc.site().isMine() }

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

    fun Location.allNeighborsWithin(distance: Int) = gameMap.filter { gameMap.getDistance(it, this) <= distance }

    // GAMEMAP

    fun playerStats() = gameMap
            .map { it.site() }
            .groupBy { it.owner }
            .mapValues { Stats(it.value.size, it.value.sumBy { it.production }, it.value.sumBy { it.strength }) }

    // CLASSES

    class RelativeLocation(val origin: Location, val direction: Direction, val loc: Location)

    // HELPER METHODS

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
}
