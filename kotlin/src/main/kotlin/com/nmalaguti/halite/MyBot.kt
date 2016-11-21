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

    var inExploration = true

    var gameLength = 0
    var numberOfPlayers = 0

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID
        points = permutations(gameMap)
        productionDensity()

        gameLength = 10 * Math.sqrt(gameMap.width * gameMap.height.toDouble()).toInt()
        numberOfPlayers = points.map { it.site().owner }.distinct().size - 1

        logger.info("id: $id")

        Networking.sendInit(BOT_NAME)
    }

    fun endGameLoop() {
        removeRepeatMoves()

        Networking.sendFrame(allMoves)
    }

    fun removeRepeatMoves() {
        // audit all moves to prevent repeated swapping
        allMoves.removeAll {
            val moveFromDestination = lastTurnMoves[it.loc.move(it.dir)]
            moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == it.loc
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

//            if (inExploration && turn > gameLength / (3 * numberOfPlayers)) {
//                logger.info("exploration = false based on time")
//                inExploration = false
//            }

            lastTurnMoves = allMoves.associateBy { it.loc }

            // reset all moves
            allMoves = mutableListOf()

//            allMoves.addAll(makePathMoves())

//            if (shortCircuit()) continue
//            updateMovedIndex()

            // make moves based on value
            if (inExploration) {
                allMoves.addAll(makeExplorationMoves())

                allMoves.removeAll {
                    val destination = it.loc.move(it.dir)
                    destination.site().isEnvironment() && (it.loc.site().strength < destination.site().strength + 1)
                }

                val outerBorderCells = points.filter { it.isOuterBorder() }
                if (inExploration && outerBorderCells.any { it.site().owner == 0 && it.site().strength < 1 }) {
                    logger.info("exploration = false based on proximity")
                    inExploration = false
                }

                endGameLoop()
                continue
            } else {
                allMoves.addAll(makeValueMoves())
            }

            if (shortCircuit()) continue
            removeRepeatMoves()
            updateMovedIndex()

            innerBorderCells = points.filter { it.isInnerBorder() }

            // make joint moves
            allMoves.addAll(makeJointMoves())

            if (shortCircuit()) continue
            removeRepeatMoves()
            updateMovedIndex()

            // make moves that abandon cells that will take too long to conquer
            allMoves.addAll(makeAbandonMoves())

            if (shortCircuit()) continue
            removeRepeatMoves()
            updateMovedIndex()

            // find a friendly unit and help out
            allMoves.addAll(makeAssistMoves())

            endGameLoop()
        }
    }

    // MOVE LOGIC

    fun makePathMoves(): List<Move> {
        val sorted = points
                .filter { !it.site().isMine() }
                .sortedByDescending { it.allNeighborsWithin(2).filter { !it.site().isMine() }.sumBy { it.site().production } }

        val top = sorted.take(2).last()

        val moves = points
                .map { it to astar(it, top)?.take(2)?.last() }
                .filter { it.second != null }
                .filter { it.first.site().strength > it.second!!.site().strength }
                .map { moveTowards(it.first, it.second!!) }

        return moves
    }

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
                    } else if (loc.site().strength > MINIMUM_STRENGTH && loc !in lastTurnMoves) {
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

    fun makeExplorationMoves(): List<Move> {
        // select a move for each point based on exploration
        val moves = points
                .filter { it.site().isMine() && it.site().strength > 0 }
                .map { loc ->
                    val site = loc.site()
                    if (site.strength > MINIMUM_STRENGTH) {
                        val best = loc.allNeighborsWithin(7)
                                .filterNot { it.site().isMine() }
                                .sortedBy { it.site().value(loc) }
                                .firstOrNull()

                        if (best != null) {
                            moveTowards(loc, best)
                        } else null
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
        if (inExploration) {
            return this.strength / Math.pow(this.production.toDouble(), 2.0)
        } else {
            if (this.isEnvironment() && this.strength > 0) {
                return this.strength / Math.pow(this.production.toDouble(), 2.0)
            } else {
                // overkill
                val strength = origin.site().strength
                val damage = this.loc.enemies()
                        .filter { !it.loc.site().isMine() }
                        .map {
                            Math.min(strength, it.loc.site().strength) + it.loc.site().production
                        }.sum() +
                        Math.min(this.strength, strength) + this.production

                return 64 / damage.toDouble()
            }
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

    fun productionDensity() {
        // points.sortedBy { it.site().production }

        val sorted = points.sortedByDescending { it.allNeighborsWithin(2).sumBy { it.site().production } }

        val playerPositions = points.filterNot { it.site().isEnvironment() }.associateBy { it.site().owner }

        val start = playerPositions[id]!!
        val goal = sorted.first()

        val path = astar(start, goal)

        logger.info("density: ${path}")
    }

    // A*

    /**
     * function A*(start, goal)
     *     // The set of nodes already evaluated.
     *     closedSet := {}
     *     // The set of currently discovered nodes still to be evaluated.
     *     // Initially, only the start node is known.
     *     openSet := {start}
     *     // For each node, which node it can most efficiently be reached from.
     *     // If a node can be reached from many nodes, cameFrom will eventually contain the
     *     // most efficient previous step.
     *     cameFrom := the empty map
     *
     *     // For each node, the cost of getting from the start node to that node.
     *     gScore := map with default value of Infinity
     *     // The cost of going from start to start is zero.
     *     gScore[start] := 0
     *     // For each node, the total cost of getting from the start node to the goal
     *     // by passing by that node. That value is partly known, partly heuristic.
     *     fScore := map with default value of Infinity
     *     // For the first node, that value is completely heuristic.
     *     fScore[start] := heuristic_cost_estimate(start, goal)
     *
     *     while openSet is not empty
     *         current := the node in openSet having the lowest fScore[] value
     *         if current = goal
     *             return reconstruct_path(cameFrom, current)
     *
     *         openSet.Remove(current)
     *         closedSet.Add(current)
     *         for each neighbor of current
     *             if neighbor in closedSet
     *                 continue		// Ignore the neighbor which is already evaluated.
     *             // The distance from start to a neighbor
     *             tentative_gScore := gScore[current] + dist_between(current, neighbor)
     *             if neighbor not in openSet	// Discover a new node
     *                 openSet.Add(neighbor)
     *             else if tentative_gScore >= gScore[neighbor]
     *                 continue		// This is not a better path.
     *
     *             // This path is the best until now. Record it!
     *             cameFrom[neighbor] := current
     *             gScore[neighbor] := tentative_gScore
     *             fScore[neighbor] := gScore[neighbor] + heuristic_cost_estimate(neighbor, goal)
     *
     *     return failure
     *
     * function reconstruct_path(cameFrom, current)
     *     total_path := [current]
     *     while current in cameFrom.Keys:
     *         current := cameFrom[current]
     *         total_path.append(current)
     *     return total_path
     */

    val INFINITY: Int = Int.MAX_VALUE / 2

    fun astar(start: Location, goal: Location): List<Location>? {

        val averageStrength = points.map { it.site().strength }.average().toInt()

        val closedSet = mutableSetOf<Location>()
        val openSet = mutableSetOf(start)

        val cameFrom = mutableMapOf<Location, Location>()

        val gScore = mutableMapOf(start to 0)
        val fScore = mutableMapOf(start to 0)

        while (openSet.isNotEmpty()) {
            var current = openSet.minBy { fScore.getOrElse(it, { INFINITY }) }!!

            if (current == goal) {
                return reconstructPath(cameFrom, current)
            }

            openSet.remove(current)
            closedSet.add(current)

            for (neighbor in current.neighbors()) {
                if (neighbor.loc in closedSet) {
                    continue
                }

                val cost = if (!neighbor.loc.site().isMine()) {
                    neighbor.loc.site().strength
                } else {
                    0
                }

                val tentativeGScore = gScore.getOrElse(current, { INFINITY }) + cost
                if (neighbor.loc !in openSet) {
                    openSet.add(neighbor.loc)
                } else if (tentativeGScore >= gScore.getOrElse(neighbor.loc, { INFINITY })) {
                    continue
                }

                cameFrom[neighbor.loc] = current
                gScore[neighbor.loc] = tentativeGScore
                fScore[neighbor.loc] = gScore[neighbor.loc]!! + gameMap.getDistance(neighbor.loc, goal).toInt() * averageStrength
            }
        }

        return null
    }

    fun reconstructPath(cameFrom: Map<Location, Location>, current: Location): List<Location> {
        var cur = current
        val totalPath = mutableListOf(cur)
        while (cur in cameFrom) {
            cur = cameFrom[cur]!!
            totalPath.add(cur)
        }
        return totalPath.reversed()
    }
}
