package com.nmalaguti.halite

val BOT_NAME = "MySavvyBot"
val MAXIMUM_TIME = 940 // ms
val PI4 = Math.PI / 4
val MINIMUM_STRENGTH = 15

object MyBot {
    lateinit var gameMap: GameMap
    var id: Int = 0
    var turn: Int = 0
    lateinit var points: List<Location>
    var allMoves = mutableSetOf<Move>()
    var start = System.currentTimeMillis()
    var movedLocations = setOf<Location>()
    var innerBorderCells: List<Location> = listOf()
    var lastTurnMoves: Map<Location, Move> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()
    var averageCost = 1

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

        val movesByDest = allMoves
                .groupBy { it.loc.move(it.dir) }

        movesByDest.filter {
            if (it.key !in movesByDest) it.value.sumBy { it.loc.site().strength } > 255
            else it.value.sumBy { it.loc.site().strength } > 255 + it.key.site().strength
        }.forEach {
            val values = it.value.toMutableSet()
            while (it.key.site().strength + values.sumBy { it.loc.site().strength } > 255) {
                val toRemove = values.filter { it.loc !in movesByDest }.firstOrNull()
                if (toRemove != null) {
                    values.remove(toRemove)
                    allMoves.remove(toRemove)
                } else {
                    allMoves.remove(values.first())
                    values.remove(values.first())
                }
            }
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

            start = System.currentTimeMillis()
            logger.info("===== Turn: ${turn++} at $start =====")

            lastTurnMoves = allMoves.associateBy { it.loc }
            playerStats = playerStats()
            averageCost = points
                    .map { it.cost() }
                    .average()
                    .toInt()

            // reset all moves
            allMoves = mutableSetOf()

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

    fun pathTowards(start: Location, end: Location): Move? {
        val path = astar(start, end)
        if (path != null && path.size > 1) {
            return moveTowards(start, path.take(2).last())
        }
        return null
    }

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

    fun Location.cost() = if (this.site().isMine())
        this.site().production * this.site().production
    else this.site().strength

    fun List<Location>.cost() = this.sumBy { it.cost() }

    fun astar(start: Location, goal: Location): List<Location>? {
        val closedSet = mutableSetOf<Location>()

        val openSet = mutableSetOf(start)

        val cameFrom = mutableMapOf<Location, Location>()

        val gScore = mutableMapOf(start to 0)
        val fScore = mutableMapOf(start to 0)

        while (openSet.isNotEmpty()) {
            val current = openSet.minBy { fScore.getOrElse(it, { INFINITY }) }!!

            if (current == goal) {
                return reconstructPath(cameFrom, current)
            }

            openSet.remove(current)
            closedSet.add(current)

            for (neighbor in current.neighbors()) {
                if (neighbor.loc in closedSet) {
                    continue
                }

                val tentativeGScore = gScore.getOrElse(current, { INFINITY }) + neighbor.loc.cost()
                if (neighbor.loc !in openSet) {
                    openSet.add(neighbor.loc)
                } else if (tentativeGScore >= gScore.getOrElse(neighbor.loc, { INFINITY })) {
                    continue
                }

                cameFrom[neighbor.loc] = current
                gScore[neighbor.loc] = tentativeGScore
                fScore[neighbor.loc] = gScore.getOrElse(neighbor.loc, { INFINITY }) +
                        (gameMap.getDistance(neighbor.loc, goal).toInt() * averageCost)
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
