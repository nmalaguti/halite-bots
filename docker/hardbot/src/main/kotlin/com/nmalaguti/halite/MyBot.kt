package com.nmalaguti.halite

import com.nmalaguti.halite.Direction
import com.nmalaguti.halite.GameMap
import com.nmalaguti.halite.Location
import com.nmalaguti.halite.MAXIMUM_STRENGTH
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
    var stillMax: Int = 0

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

            stillMax = 0

            makeBattleMoves()

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

    // MOVE LOGIC

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

    fun walkGridFrom(openSet: MutableSet<Location>, closedSet: MutableSet<Location>): Boolean {
        var changed = false
        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isMine()) {
                    val prevValue = distanceToEnemyGrid[current.y][current.x]
                    distanceToEnemyGrid[current.y][current.x] =
                            Math.min(
                                    distanceToEnemyGrid[current.y][current.x],
                                    //                                    (territory / border.toDouble()).toInt() +
                                    1 +
                                            current.neighbors().map { distanceToEnemyGrid[it.y][it.x] }.min()!! +
                                            (Math.max(0.0, Math.log(current.site().production.toDouble() / Math.log(2.0))).toInt())
                            )
                    if (prevValue != distanceToEnemyGrid[current.y][current.x]) changed = true
                }

                current.neighbors()
                        .filterNot { it.site().isEnvironment() && it.site().strength > 0 }
                        .forEach { openSet.add(it) }
            }
        }

        return changed
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
        val blackoutCells = mutableSetOf<Location>()

        val sources = mutableMapOf<Location, Direction>()
        val destinations = mutableMapOf<Location, Direction>()

        fun Location.swappable(source: Location) =
                this != source &&
                        this.site().isMine() &&
                        (this !in sources || sources[this] == Direction.STILL) &&
                        (this !in destinations || destinations[this] == Direction.STILL) &&
                        ((source.site().strength == 255 && this.site().strength < 255) ||
                                this.site().strength + 15 < source.site().strength)

        fun finalizeMove(source: Location, target: Location, blackout: Boolean) {
            if (target.swappable(source) &&
                    nextMap.getSite(target).strength + source.site().strength >= MAXIMUM_STRENGTH) {

                // swap
                val move1 = moveTowards(source, target)
                val move2 = moveTowards(target, source)

                updateNextMap(move1)
                updateNextMap(move2)

                allMoves.add(move1)
                allMoves.add(move2)

                sources.put(source, move1.dir)
                sources.put(target, move2.dir)
                destinations.put(source, move2.dir)
                destinations.put(target, move1.dir)
            } else {
                var move = moveTowards(source, target)

                if (!blackout) {
                    val moveFromDestination = lastTurnMoves[move.loc.move(move.dir)]
                    if (moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == move.loc) {
                        move = Move(source, Direction.STILL)
                    }

                    if (nextMap.getSite(target).isMine() && gameMap.getSite(source).strength + nextMap.getSite(target).strength >= MAXIMUM_STRENGTH) {
                        move = Move(source, Direction.STILL)
                    }
                }

                updateNextMap(move)

                allMoves.add(move)

                if (target != source) blackoutCells.add(source)

                sources.put(source, move.dir)
                destinations.put(target, move.dir)
                if (source.site().strength == 255 && move.dir == Direction.STILL) stillMax += 1
            }
        }

        gameMap
                .filter { it.site().isMine() && it.site().strength > 0 }
                .filter { it.neighbors().any { it.site().isEnvironment() && it.site().strength == 0 } }
                .sortedByDescending { it.site().strength }
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    // on the edge of battle
                    val target =
                            if (loc.site().strength < loc.site().production * 2 || loc.site().strength < MINIMUM_STRENGTH) loc
                            else loc.neighbors()
                                    .filter { it.site().isEnvironment() && it.site().strength == 0 }
                                    .filter { it !in blackoutCells }
                                    .filter {
                                        nextMap.getSite(it).strength + loc.site().strength < MAXIMUM_STRENGTH ||
                                                it.swappable(loc)
                                    }
                                    .sortedByDescending { it.overkill() }
                                    .firstOrNull() ?: loc

                    finalizeMove(loc, target, true)
                }

        gameMap
                .filter { it.site().isMine() && it !in sources }
                .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { -it.site().strength }, { it.neighbors().filterNot { it.site().isMine() }.size }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    var target = loc.neighbors()
                            .filter { it !in blackoutCells }
                            .filter { distanceToEnemyGrid[it.y][it.x] < distanceToEnemyGrid[loc.y][loc.x] }
                            .filter {
                                val nextSite = nextMap.getSite(it)

                                if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                    // enemy space
                                    loc.site().strength > Math.max(loc.site().production * 2, MINIMUM_STRENGTH) &&
                                            nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH
                                } else if (nextSite.isEnvironment()) {
                                    // environment
                                    nextSite.strength < loc.site().strength
                                } else {
                                    // mine
                                    loc.site().strength > Math.max(loc.site().production * 5, MINIMUM_STRENGTH * 2) &&
                                            (nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH || it.swappable(loc))
                                }
                            }
                            .sortedByDescending { it.overkill() }
                            .firstOrNull()

                    if (target != null) {
                        if (target in blackoutCells) target = loc
                        finalizeMove(loc, target!!, false)
                    } else {
                        if (loc.site().strength == 255) {
                            stillMax += 1
                        }
                    }
                }
    }

    fun updateNextMap(move: Move) {
        val source = move.loc
        val target = move.loc.move(move.dir)

        val originSite = gameMap.getSite(source)
        val startSite = nextMap.getSite(source)
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
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isMine() = this.owner == id

    fun Location.overkill() =
            if (this.site().isEnvironment() && this.site().strength > 0) {
                -distanceToEnemyGrid[this.y][this.x]
            } else {
                this.neighbors()
                        .map {
                            if (it.site().isOtherPlayer()) it.site().strength + it.site().production
                            else if (nextMap.getSite(it).isMine()) it.allNeighborsWithin(2)
                                    .filter { it.site().isMine() }
                                    .map { -nextMap.getSite(it).strength }
                                    .sum()
                            else 0
                        }.sum()
            }

    fun Site.resource() = if (!this.isMine()) {
        Math.max(0, (this.strength / (this.production + stillMax).toDouble()).toInt())
    }
    else 9999

    // LOCATION

    fun Location.isInnerBorder() =
            this.site().isMine() &&
                    this.neighbors().any { !it.site().isMine() }

    fun Location.isOuterBorder() =
            !this.site().isMine() &&
                    this.neighbors().any { it.site().isMine() }

    fun Location.neighbors() = Direction.CARDINALS.map { this.move(it) }

    fun Location.neighborsAndSelf() = Direction.DIRECTIONS.map { this.move(it) }

    fun Location.enemies() = this.neighbors().filterNot { it.site().isMine() }

    fun Location.friends() = this.neighbors().filter { it.site().isMine() }

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

    // HELPER METHODS

    fun permutations(first: IntRange, second: IntRange) =
            first.flatMap { y ->
                second.map { x ->
                    Location(x, y)
                }
            }

    fun permutations(gameMap: GameMap) = permutations(0 until gameMap.height, 0 until gameMap.width)

    fun moveTowards(start: Location, end: Location): Move {
        if (start == end) return Move(start, Direction.STILL)
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
