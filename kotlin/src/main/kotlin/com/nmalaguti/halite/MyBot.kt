package com.nmalaguti.halite

val BOT_NAME = "MySimpleBot"
val MAXIMUM_TIME = 940 // ms
val PI4 = Math.PI / 4
val MINIMUM_STRENGTH = 15

object MyBot {
    lateinit var gameMap: GameMap
    lateinit var nextMap: GameMap
    var id: Int = 0
    var turn: Int = 0
    lateinit var points: List<Location>
    var allMoves = mutableSetOf<Move>()
    var start = System.currentTimeMillis()
    var lastTurnMoves: Map<Location, Move> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()
    var distanceToEnemyGrid = mutableListOf<MutableList<Int>>()

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID
        points = permutations(gameMap)

        logger.info("id: $id")

        Networking.sendInit(BOT_NAME)
    }

    fun endGameLoop() {
        removeUnwiseMoves()

        Networking.sendFrame(allMoves)
    }

    fun removeUnwiseMoves() {
        // audit all moves to prevent repeated swapping
        allMoves.removeAll {
            val moveFromDestination = lastTurnMoves[it.loc.move(it.dir)]
            moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == it.loc
        }
    }

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

            lastTurnMoves = allMoves.associateBy { it.loc }
            playerStats = playerStats()

            // reset all moves
            allMoves = mutableSetOf()

            buildDistanceToEnemyGrid()

            makeBattleMoves()

            endGameLoop()
        }
    }

    // MOVE LOGIC

    var highestAvailableProduction = 0

    fun buildDistanceToEnemyGrid() {
        distanceToEnemyGrid = mutableListOf<MutableList<Int>>()
        for (y in 0 until gameMap.height) {
            val row = mutableListOf<Int>()
            for (x in 0 until gameMap.width) {
                val site = Location(x, y).site()
                val value = if (site.isEnvironment()) (site.strength / site.production.toDouble()).toInt()
                else if (site.isOtherPlayer()) 0
                else 999
                row.add(value)
            }
            distanceToEnemyGrid.add(row)
        }

        val openSet = mutableSetOf<Location>()
        val closedSet = mutableSetOf<Location>()

        openSet.addAll(points.filter { it.site().isOtherPlayer() })

        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isMine()) {
                    distanceToEnemyGrid[current.y][current.x] =
                            Math.min(
                                    distanceToEnemyGrid[current.y][current.x],
                                    1 + current.neighbors().map { distanceToEnemyGrid[it.loc.y][it.loc.x] }.min()!!
                            )
                }

                current.neighbors()
                        .filterNot { it.loc.site().isEnvironment() && it.loc.site().strength > 0 }
                        .forEach { openSet.add(it.loc) }
            }
        }

        openSet.addAll(points.filter { it.isOuterBorder() })

        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isMine()) {
                    distanceToEnemyGrid[current.y][current.x] =
                            Math.min(
                                    distanceToEnemyGrid[current.y][current.x],
                                    1 + current.neighbors().map { distanceToEnemyGrid[it.loc.y][it.loc.x] }.min()!!
                            )
                }

                current.neighbors()
                        .filterNot { it.loc.site().isEnvironment() && it.loc.site().strength > 0 }
                        .forEach { openSet.add(it.loc) }
            }
        }

        // logDistanceToEnemyGrid()
    }

    fun logDistanceToEnemyGrid() {
        val builder = StringBuilder("\n")

        for (y in 0 until gameMap.height) {
            builder.append(distanceToEnemyGrid[y].map { "$it".take(3).padEnd(4) }.joinToString(" "))
            builder.append("\n")
        }

        logger.info(builder.toString())
    }

    fun makeBattleMoves() {
        points
                .filter { it.site().isMine() }
                .sortedBy { distanceToEnemyGrid[it.y][it.x] }
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val target =
                            if (loc.site().strength <= Math.min(loc.site().production * 4, MINIMUM_STRENGTH * 3)) {
                                null
                            } else if (distanceToEnemyGrid[loc.y][loc.x] > 2) {
                                loc.neighbors()
                                        .filter { distanceToEnemyGrid[it.loc.y][it.loc.x] < distanceToEnemyGrid[loc.y][loc.x] }
                                        .filter {
                                            val nextSite = nextMap.getSite(it.loc)
                                            if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                                // enemy space
                                                nextSite.strength + loc.site().strength < 287
                                            } else if (nextSite.isEnvironment()) {
                                                // environment
                                                nextSite.strength < loc.site().strength
                                            } else {
                                                // mine
                                                nextSite.strength + loc.site().strength < 287
                                            }
                                        }
                                        .firstOrNull()?.loc
                            } else {
                                loc.allNeighborsWithin(3)
                                        .filter { distanceToEnemyGrid[it.y][it.x] < distanceToEnemyGrid[loc.y][loc.x] }
                                        .filter { !it.site().isMine() }
                                        .filter {
                                            val next = moveTowards(loc, it)
                                            val nextSite = nextMap.getSite(next.loc, next.dir)
                                            if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                                // enemy space
                                                nextSite.strength + loc.site().strength < 287
                                            } else if (nextSite.isEnvironment()) {
                                                // environment
                                                nextSite.strength < loc.site().strength
                                            } else {
                                                // mine
                                                nextSite.strength + loc.site().strength < 287
                                            }
                                        }
                                        .sortedByDescending { it.site().overkill() }
                                        .firstOrNull()
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
                    } else {
                        // why?

                        if (loc.site().strength > 175) {
                            val env = loc.neighbors().find { !it.loc.site().isMine() }
                            if (env != null) {
                                allMoves.add(moveTowards(loc, env.loc))
                            }
                        }
                    }
                }
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isMine() = this.owner == id

    fun Site.overkill() = this.loc.enemies()
            .filter { it.loc.site().isOtherPlayer() }
            .map {
                it.loc.site().strength + it.loc.site().production
            }.sum()

    fun Site.value(): Double {
        if (this.isEnvironment() && this.strength > 0) {
            return this.strength / this.production.toDouble()
        } else {
            // overkill
            val damage = this.loc.enemies()
                    .filter { it.loc.site().isOtherPlayer() }
                    .map {
                        it.loc.site().strength + it.loc.site().production
                    }.sum()

            return -damage.toDouble()
        }
    }

    // LOCATION

    fun Location.isInnerBorder() =
            this.site().isMine() &&
                    this.neighbors().any { !it.loc.site().isMine() }

    fun Location.isOuterBorder() =
            !this.site().isMine() &&
                    this.neighbors().any { it.loc.site().isMine() }

    fun Location.neighbors() = Direction.CARDINALS.map { RelativeLocation(this, it) }

    fun Location.enemies() = this.neighbors().filterNot { it.loc.site().isMine() }

    fun Location.friends() = this.neighbors().filter { it.loc.site().isMine() }

    fun Location.bestTarget(): RelativeLocation? = this.enemies().sortedBy { it.loc.site().value() }.firstOrNull()

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

    fun Location.allNeighborsWithin(distance: Int) = points.filter { gameMap.getDistance(it, this) <= distance }

    // GAMEMAP

    fun playerStats() = points
            .map { it.site() }
            .groupBy { it.owner }
            .mapValues { Stats(it.value.size, it.value.sumBy { it.production }, it.value.sumBy { it.strength }) }

    // CLASSES

    class RelativeLocation(val origin: Location, val direction: Direction) {
        val loc: Location = origin.move(direction)
    }

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
}
