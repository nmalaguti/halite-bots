package com.nmalaguti.halite

val BOT_NAME = "MySmarterBot"
val MAXIMUM_TIME = 940 // ms
val PI4 = Math.PI / 4
val MINIMUM_STRENGTH = 15

object MyBot {
    lateinit var gameMap: GameMap
    var id: Int = 0
    var turn: Int = 0
    lateinit var points: List<Location>
    var allMoves = mutableListOf<Move>()
    var start = System.currentTimeMillis()
    var movedLocations = setOf<Location>()
    var innerBorderCells: List<Location> = listOf()
    var lastTurnMoves: Map<Location, Move> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()

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

        // remove moves that attack other players with too little strength
        allMoves.removeAll {
            val destination = it.loc.move(it.dir)

            destination.site().isEnvironment() && destination.site().strength == 0 &&
                    it.loc.site().strength <  Math.min(it.loc.site().production * 2, MINIMUM_STRENGTH)
        }
    }

    fun shortCircuit() = if (System.currentTimeMillis() - start > MAXIMUM_TIME) {
        endGameLoop()
        true
    } else false

    fun updateMovedIndex() {
        movedLocations = allMoves.map { it.loc }.toSet()
    }

    @Throws(java.io.IOException::class)
    @JvmStatic fun main(args: Array<String>) {
        initializeLogging()
        init()

        // game loop
        while (true) {
            // get frame
            gameMap = Networking.getFrame()
            playerStats = playerStats()

            start = System.currentTimeMillis()
            logger.info("===== Turn: ${turn++} at $start =====")

            lastTurnMoves = allMoves.associateBy { it.loc }

            // reset all moves
            allMoves = mutableListOf()

            // make moves based on value
            allMoves.addAll(makeValueMoves())

            if (shortCircuit()) continue
            removeUnwiseMoves()
            updateMovedIndex()

            innerBorderCells = points.filter { it.isInnerBorder() }

            // make joint moves
            allMoves.addAll(makeJointMoves())

            if (shortCircuit()) continue
            removeUnwiseMoves()
            updateMovedIndex()

            // make moves that abandon cells that will take too long to conquer
            allMoves.addAll(makeAbandonMoves())

            if (shortCircuit()) continue
            removeUnwiseMoves()
            updateMovedIndex()

            // find a friendly unit and help out
            allMoves.addAll(makeAssistMoves())

            endGameLoop()
        }
    }

    // MOVE LOGIC

    fun makeValueMoves(): List<Move> {
        // select a move for each point based on site value
        val moves = points
                .filter { it.site().isMine() && it.site().strength > 0 }
                .map { loc ->
                    val site = loc.site()
                    if (loc.isInnerBorder()) {
                        val targets = loc.enemies().filter { site.strength > it.loc.site().strength }

                        if (targets.isNotEmpty()) {
                            val best = targets.sortedBy { it.loc.site().value(it.origin) }.first()
                            Move(best.origin, best.direction)
                        } else null
                    } else if (loc.site().strength > Math.min(loc.site().production * 4, MINIMUM_STRENGTH * 3 + 1)
                            && loc !in lastTurnMoves) {
                        val best = loc.allNeighborsWithin(7)
                                .filterNot { it.site().isMine() }
                                .sortedBy { it.site().value(loc) }
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
        // abandon cells that will take too long to conquer
        val moves = innerBorderCells
                .filterNot { it in movedLocations }
                .filter { it.site().strength > MINIMUM_STRENGTH }
                .map { it.enemies() }
                .filter { it.isNotEmpty() }
                .map {
                    val sorted = it.sortedBy { it.loc.site().value(it.origin) }
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
                                        friendBestTarget.loc.site().value(friendBestTarget.origin) < bestTarget.loc.site().value(bestTarget.origin) &&
                                        // our combined strength can take the target
                                        friend.loc.site().strength + self.site().strength > friendBestTarget.loc.site().strength) {
                                    friend to friendBestTarget
                                } else null
                            }
                            .filterNotNull()
                            .sortedBy { it.second.loc.site().value(it.second.origin) }
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

    fun Site.value(origin: Location): Double {
        if (this.isEnvironment() && this.strength > 0) {
            return (this.strength + origin.site().production) / Math.pow(this.production.toDouble(), 2.0)
        } else {
            // overkill
            val strength = origin.site().strength
            val damage = this.loc.enemies()
                    .filter { it.loc.site().isOtherPlayer() }
                    .map {
                        Math.min(strength, it.loc.site().strength) + it.loc.site().production
                    }.sum() +
                    Math.min(this.strength, strength) + this.production


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

    fun Location.neighbors() = Direction.CARDINALS.map { RelativeLocation(this, it) }

    fun Location.enemies() = this.neighbors().filterNot { it.loc.site().isMine() }

    fun Location.friends() = this.neighbors().filter { it.loc.site().isMine() }

    fun Location.bestTarget(): RelativeLocation? = this.enemies().sortedBy { it.loc.site().value(it.origin) }.firstOrNull()

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

    fun Location.allNeighborsWithin(distance: Int) = points.filter { gameMap.getDistance(it, this) < distance }

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
