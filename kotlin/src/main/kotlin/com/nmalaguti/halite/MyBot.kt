package com.nmalaguti.halite

import kotlin.comparisons.compareBy

val BOT_NAME = "MyExoticBot"
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

    fun startGameLoop() {
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

        innerBorderCells = points.filter { it.isInnerBorder() }

        // reset all moves
        allMoves = mutableSetOf()
    }

    fun endGameLoop() {
        Networking.sendFrame(allMoves)
    }

    @Throws(java.io.IOException::class)
    @JvmStatic fun main(args: Array<String>) {
        initializeLogging()
        init()

        // game loop
        while (true) {
            startGameLoop()

            makeMoves()

            endGameLoop()
        }
    }

    // MOVE LOGIC

    fun makeMoves() {

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

    fun Location.allNeighborsWithin(distance: Int) = points
            .filter { gameMap.getDistance(it, this) <= distance }
            .toSet()
            .minus(this)

    fun Location.allNeighborsAt(distance: Int) = points
            .filter {
                val locDist = gameMap.getDistance(it, this)
                locDist > distance - 1 && locDist <= distance }
            .toSet()
            .minus(this)

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
        return totalPath.take(totalPath.size - 1).reversed()
    }
}
