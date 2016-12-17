package com.nmalaguti.halite

import java.util.*
import kotlin.comparisons.compareBy

val BOT_NAME = "MyDirectedWalkBot"
val MAXIMUM_TIME = 940 // ms
val PI4 = Math.PI / 4
val MINIMUM_STRENGTH = 15
val MAXIMUM_STRENGTH = 256

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
    var stillMax: Int = 0
    var territory: Int = 0
    var border: Int = 0
    var madeContact: Boolean = false

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID
        points = permutations(gameMap)

        logger.info("id: $id")

        Networking.sendInit(BOT_NAME)
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

            stillMax = 0
            territory = points.filter { it.site().isMine() }.size
            border = points.filter { it.isInnerBorder() }.size

            if (points.any { it.site().isEnvironment() && it.site().strength == 0 && it.neighbors().any { it.loc.site().isMine() }}) {
                madeContact = true
            }

            makeBattleMoves()

            logger.info("stillMax: $stillMax")
            logger.info("additive: ${(territory / border.toDouble()).toInt()}")

            endGameLoop()
        }
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

        // update outer border based on random walks?
//        points
//                .filter { it.isOuterBorder() && it.site().isEnvironment() && it.site().strength > 0 }
//                .map { it to directedWalk(it) }
//                .forEach {
//                    val (loc, value) = it
//                    distanceToEnemyGrid[loc.y][loc.x] = value
//                }

        points.filter { it.isOuterBorder() }.sortedBy { distanceToEnemyGrid[it.y][it.x] }.take(20).forEach {
            walkGridFrom(mutableSetOf(it), mutableSetOf())
        }

        logDistanceToEnemyGrid()
    }

    fun directedWalk(loc: Location): Int {
        val locToValue = mutableMapOf<Location, Double>()
        var minAvg: Double = loc.site().resource().toDouble()
        var queue = ArrayDeque<Location>()
        queue.addFirst(loc)
        locToValue[loc] = minAvg
        val visited = mutableSetOf<Location>()

        while (queue.isNotEmpty()) {
            val currLoc = queue.removeFirst()

            if (currLoc in visited) continue
            visited.add(currLoc)

            val dist = gameMap.getDistance(currLoc, loc)

            if (dist > 9) continue

            val currAvg = locToValue[currLoc] ?: minAvg

            if (currAvg < minAvg) minAvg = currAvg

            currLoc.neighbors()
                    .filter { it.loc.site().isEnvironment() && it.loc.site().strength > 0 }
                    .forEach {
                        val nextValue = currAvg - ((currAvg - it.loc.site().resource().toDouble()) / ((dist + 3)))
                        val currValue = locToValue.getOrPut(it.loc, { nextValue })
                        if (nextValue < currValue) locToValue[it.loc] = nextValue
                        queue.addLast(it.loc)
                    }
        }

        return minAvg.toInt()
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
                                            current.neighbors().map { distanceToEnemyGrid[it.loc.y][it.loc.x] }.min()!! +
                                            (Math.max(0.0, Math.log(current.site().production.toDouble() / Math.log(2.0))).toInt())
                            )
                    if (prevValue != distanceToEnemyGrid[current.y][current.x]) changed = true
                }

                current.neighbors()
                        .filterNot { it.loc.site().isEnvironment() && it.loc.site().strength > 0 }
                        .forEach { openSet.add(it.loc) }
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

        fun updateNextMap(move: Move) {
            val source = move.loc
            val target = move.loc.move(move.dir)

            val originSite = gameMap.getSite(source)
            val targetSite = gameMap.getSite(target)
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

                if (target !in destinations) {
                    destinationSite.strength = 0
                }

                if (targetSite.strength <= originSite.strength) {
                    targetSite.strength = 0
                    destinationSite.strength += originSite.strength
                    destinationSite.owner = originSite.owner
                } else {
                    targetSite.strength -= originSite.strength
                    destinationSite.strength += originSite.strength
                }
            }
        }

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

                allMoves.add(move)

                if (target != source) blackoutCells.add(source)

                sources.put(source, move.dir)
                destinations.put(target, move.dir)
                if (source.site().strength == 255 && move.dir == Direction.STILL) stillMax += 1

//                if (nextMap.getSite(target).isEnvironment() && nextMap.getSite(target).strength == 0) {
//                    target.neighbors()
//                            .filter { it.loc != source && it.loc !in sources && it.loc !in destinations}
//                            .forEach { dangerLoc ->
//                                // move them away?
//                                dangerLoc.loc.neighbors()
//                                        .filter { it.loc !in sources && it.loc !in destinations && it.loc != target && nextMap.getSite(it.loc).isMine() }
//                                        .sortedBy { nextMap.getSite(it.loc).strength }
//                                        .firstOrNull()
//                                        ?.let {
//                                            logger.info("danger move! ${dangerLoc.loc} to ${it.loc}")
//                                            finalizeMove(dangerLoc.loc, it.loc, true)
//                                        }
//                            }
////                    blackoutCells.addAll(target.neighbors().map { it.loc })
//                }

                updateNextMap(move)

//                target.neighbors()
//                        .filter { nextMap.getSite(it.loc).isEnvironment() && nextMap.getSite(it.loc).strength == 0 }
//                        .forEach {
//                            blackoutCells.addAll(it.loc.neighborsAndSelf().map { it.loc })
//                        }
            }
        }

//        fun allCombos(locs: List<RelativeLocation>): List<List<RelativeLocation>> {
//            val combos = mutableListOf<List<RelativeLocation>>()
//
//            (0 until locs.size).forEach { a ->
//                combos.add(listOf(locs[a]))
//
//                ((a + 1) until locs.size).forEach { b ->
//                    combos.add(listOf(locs[a], locs[b]))
//
//                    ((b + 1) until locs.size).forEach { c ->
//                        combos.add(listOf(locs[a], locs[b], locs[c]))
//
//                        ((c + 1) until locs.size).forEach { d ->
//                            combos.add(listOf(locs[a], locs[b], locs[c], locs[d]))
//                        }
//                    }
//                }
//            }
//
//            return combos
//        }

        points
                .filter { it.site().isMine() && it.site().strength > 0 }
                .filter { it.neighbors().any { it.loc.site().isEnvironment() && it.loc.site().strength == 0 } }
                .sortedByDescending { it.site().strength }
                .forEach { loc ->
                    // on the edge of battle
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    if (loc in sources) return@forEach

                    val target =
                            if (loc.site().strength < loc.site().production * 2 || loc.site().strength < MINIMUM_STRENGTH) loc
                            else loc.neighbors()
                                    .filter { it.loc.site().isEnvironment() && it.loc.site().strength == 0 }
                                    .filter { it.loc !in blackoutCells }
                                    .filter {
                                        nextMap.getSite(it.loc).strength + loc.site().strength < MAXIMUM_STRENGTH ||
                                                it.loc.swappable(loc)
                                    }
                                    .sortedByDescending { it.loc.site().overkill() }
                                    .firstOrNull()?.loc ?: loc

                    finalizeMove(loc, target, true)
                }
//
//        points
//                .filter { it.isOuterBorder() && it.site().isEnvironment() && it.site().strength > 0 }
//                .sortedBy { distanceToEnemyGrid[it.y][it.x] }
//                .forEach { loc ->
//                    val wouldAttack = loc.neighbors()
//                            .filter { it.loc.site().isMine() && it.loc !in sources && it.loc.site().strength > 0 }
//                            .filter { distanceToEnemyGrid[it.loc.y][it.loc.x] > distanceToEnemyGrid[loc.y][loc.x] }
//                            .filter { it.loc.site().production > 0 && (it.loc.site().strength - loc.site().strength) / it.loc.site().production > 3 }
//
//                    allCombos(wouldAttack)
//                            .sortedWith(compareBy({ it.size }, { it.map { it.loc.site().strength }.sum() }))
//                            .filter {
//                                val sum = it.sumBy { it.loc.site().strength }
//                                sum > loc.site().strength && sum < MAXIMUM_STRENGTH
//                            }
//                            .firstOrNull()
//                            ?.forEach {
//
//                                finalizeMove(it.loc, loc, true)
//                            }
//                }

        points
                .filter { it.site().isMine() && it !in sources }
                .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { -it.site().strength }, { it.neighbors().filterNot { it.loc.site().isMine() }.size }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    var target = loc.neighbors()
                            .filter { it.loc !in blackoutCells }
                            .filter { distanceToEnemyGrid[it.loc.y][it.loc.x] < distanceToEnemyGrid[loc.y][loc.x] }
                            .filter {
                                val nextSite = nextMap.getSite(it.loc)

                                if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                    // enemy space
                                    loc.site().strength > loc.site().production * multiplier(loc) &&
                                            nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH
                                } else if (nextSite.isEnvironment()) {
                                    // environment
                                    it.loc.site().strength < loc.site().strength
                                } else {
                                    // mine
                                    loc.site().strength > loc.site().production * multiplier(loc) &&
                                            (nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH || it.loc.swappable(loc))
                                }
                            }
                            .sortedByDescending { it.loc.site().overkill() }
                            .firstOrNull()?.loc

                    if (target != null) {
                        if (target in blackoutCells) target = loc
                        finalizeMove(loc, target, false)
                    } else {
                        if (loc.site().strength == 255) {
                            stillMax += 1
                        }
                    }
                }
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isMine() = this.owner == id

//    fun Site.overkill() =
//            if (this.isMine() || (this.isEnvironment() && this.strength > 0)) {
//                -distanceToEnemyGrid[this.loc.y][this.loc.x]
//            } else {
//                this.loc.neighbors()
//                        .map {
//                            if (it.loc.site().isOtherPlayer()) it.loc.site().strength + it.loc.site().production
//                            else 0
//                        }.sum()
//            }

    fun Site.overkill() =
            if (this.isEnvironment() && this.strength > 0) {
                -distanceToEnemyGrid[this.loc.y][this.loc.x]
            } else {
                this.loc.neighbors()
                        .map {
                            if (it.loc.site().isOtherPlayer()) it.loc.site().strength + it.loc.site().production
                            else if (nextMap.getSite(it.loc).isMine()) it.loc.allNeighborsWithin(2)
                                    .filter { it.site().isMine() }
                                    .map { -nextMap.getSite(it).strength }
                                    .sum()
                            else 0
                        }.sum()
            }

//    fun Site.resource() = if (!this.isMine()) {
////        (this.strength / (this.production + stillMax).toDouble()).toInt()
//        if (this.strength == 0) this.loc.allNeighborsWithin(4).filter { it.site().isMine() && it.site().strength > 240 }.size
//        else (this.strength / (this.production + (stillMax / 2)).toDouble()).toInt()
//    }
//    else 9999

    fun Site.resource() = if (!this.isMine()) {
//        (this.strength / (this.production + stillMax).toDouble()).toInt()
        if (this.strength == 0) this.loc.allNeighborsWithin(4).filter { it.site().isMine() && it.site().strength > 240 }.size
        else (this.loc.neighbors()
                .filter { it.loc.site().isEnvironment() && it.loc.site().strength > 0 && it.loc.site().production > 0 }
                .map {
                    (it.loc.site().strength / (it.loc.site().production + (stillMax / 2)).toDouble())
                }
                .average() + (this.strength / (this.production + (stillMax / 2)).toDouble()))
                .toInt() / 2
    }
    else 9999

    // LOCATION

    fun Location.isInnerBorder() =
            this.site().isMine() &&
                    this.neighbors().any { !it.loc.site().isMine() }

    fun Location.isOuterBorder() =
            !this.site().isMine() &&
                    this.neighbors().any { it.loc.site().isMine() }

    fun Location.neighbors() = Direction.CARDINALS.map { RelativeLocation(this, it) }

    fun Location.neighborsAndSelf() = Direction.DIRECTIONS.map { RelativeLocation(this, it) }

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

    fun log2(num: Double) = Math.log(num) / Math.log(2.0)

    fun multiplier(loc: Location) = Math.max(1, Math.min(5.0, log2(distanceToEnemyGrid[loc.y][loc.x].toDouble() * 1.5)).toInt())
}
