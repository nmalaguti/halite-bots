package com.nmalaguti.halite

import java.util.*
import kotlin.comparisons.compareBy

val BOT_NAME = "MyLessTrafficBot"
val MAXIMUM_TIME = 940 // ms
val PI4 = Math.PI / 4
val MINIMUM_STRENGTH = 15
val MAXIMUM_STRENGTH = 256

object MyBot {
    lateinit var gameMap: GameMap
    lateinit var nextMap: GameMap
    var id: Int = 0
    var turn: Int = 0
    var allMoves = mutableMapOf<Location, Direction>()
    var start = System.currentTimeMillis()
    var lastTurnMoves: Map<Location, Direction> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()
    var distanceToEnemyGrid = mutableListOf<MutableList<Int>>()
    var stillMax: Int = 0
    var territory: Int = 0
    var border: Int = 0
    var madeContact: Boolean = false
    var numPlayers: Int = 0
    var enableDirectedWalk: Boolean = false

    var resourceStrategy: ResourceStrategy = ResourceStrategy.Pure
    var growthStrategy: GrowthStrategy = GrowthStrategy.Dynamic


    @Throws(java.io.IOException::class)
    @JvmStatic fun main(args: Array<String>) {
        initializeLogging()
        init()

        while (true) {
            gameLoop(Networking.getFrame())
            Networking.sendFrame(sendMoves())
        }
    }

    fun reset() {
        turn = 0
        allMoves = mutableMapOf()
        stillMax = 0
        madeContact = false
    }

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID

        numPlayers = gameMap.map { gameMap.getSite(it).owner }.filter { it > 0 }.toSet().size

        logger.info("id: $id")
        logger.info("numPlayers: $numPlayers")

        // 15 seconds to test out which behavior will work best
        preprocess()

        logger.info("resource strategy: $resourceStrategy")
        logger.info("growth strategy: $growthStrategy")

        reset()

        Networking.sendInit(BOT_NAME)
    }

    fun preprocess() {
        val preprocessStart = System.currentTimeMillis()

        val initialGameMap = GameMap(gameMap)
        val strategies = mutableListOf(
                ResourceStrategy.Pure to GrowthStrategy.StaticWithMinumum,
                ResourceStrategy.Average to GrowthStrategy.StaticWithMinumum,
                ResourceStrategy.Pure to GrowthStrategy.DynamicWithMinimum,
                ResourceStrategy.Average to GrowthStrategy.DynamicWithMinimum
        ).shuffle()

        val results = mutableListOf<Pair<Stats, Pair<ResourceStrategy, GrowthStrategy>>>()

        strategies.forEach strats@ {
            resourceStrategy = it.first
            growthStrategy = it.second

            reset()

            gameMap = GameMap(initialGameMap)

            (0 until 50).forEach {
                if (System.currentTimeMillis() - preprocessStart > 14000) return@strats

                val startGameMap = GameMap(gameMap)
                gameLoop(startGameMap)
                gameMap = processFrame(startGameMap, sendMoves(), numPlayers)
            }

            playerStats()
            results.add(playerStats[id]!! to it)
        }

        results.sortWith(compareBy({ -it.first.production }))

        logger.info(results.map { "${it.second}: str(${it.first.strength}) prd(${it.first.production}) ter(${it.first.territory})" }.joinToString("\n"))

        results.firstOrNull()?.let {
            resourceStrategy = it.second.first
            growthStrategy = it.second.second
        }
    }

    fun gameLoop(map: GameMap) {
        // get frame
        gameMap = map
        nextMap = GameMap(gameMap)

        start = System.currentTimeMillis()
        logger.info("===== Turn: ${turn++} at $start =====")

        lastTurnMoves = allMoves
        playerStats = playerStats()

        // reset all moves
        allMoves = mutableMapOf()
        territory = gameMap.filter { it.site().isMine() }.size
        border = gameMap.filter { it.isInnerBorder() }.size
        if (gameMap.any { it.site().isCombat() && it.neighbors().any { it.site().isMine() }}) {
            madeContact = true
        }

        buildDistanceToEnemyGrid()

        makeBattleMoves()

        logger.info("stillMax: $stillMax")
        logger.info("additive: ${(territory / border.toDouble()).toInt()}")
    }

    // MOVE LOGIC

    fun buildDistanceToEnemyGrid() {
         mutableListOf<MutableList<Int>>()

        distanceToEnemyGrid = (0 until gameMap.height)
                .map { y ->
                    (0 until gameMap.width)
                            .map { x -> Location(x, y).site().resource() }
                            .toMutableList()
                }
                .toMutableList()

        val distanceFromCombat = (0 until gameMap.height)
                .map { y ->
                    (0 until gameMap.width)
                            .map { x ->
                                val loc = Location(x, y)

                                if (loc.site().isCombat()) 0
                                else 9999
                            }
                            .toMutableList()
                }
                .toMutableList()

        walkCombatGrid(distanceFromCombat)

        gameMap
                .filter { it.isInnerBorder() }
                .map {
                    tunnel(it)
                }
                .filterNotNull()
                .sortedBy { it.second }
                .firstOrNull()
                ?.let {
                    if (playerStats[id]?.production ?: 0 > playerStats[it.first.site().owner]?.production ?: 0) {
                        distanceToEnemyGrid[it.first.y][it.first.x] = 0
                        logger.info("mystery loc: ${it.first}")
                    }
                }

        val collected = mutableSetOf<Location>()

//        gameMap
//                .filter { it.site().isCombat() }
//                .map {
//                    if (it in collected) null
//                    else {
//                        val sphere = walkToDrop(it, distanceFromCombat)
//                        collected.addAll(sphere)
//                        it to sphere.partition { it.site().isMine() }
//                    }
//                }
//                .filterNotNull()
//                .forEach {
//                    val loc = it.first
//                    val (mine, enemies) = it.second
//
//                    val myStrength = mine.map { it.site().strength }.sum()
//                    val enemyStrength = enemies.map { it.site().strength }.sum()
//
//                    logger.info("strength around $loc: $myStrength | $enemyStrength")
//
//                    distanceToEnemyGrid[loc.y][loc.x] = Math.max(0, (myStrength - enemyStrength) / 255)
//
////                    if (myStrength > enemyStrength) {
//////                        // find a place to tunnel
//////                        val tunnelPoint = mine
//////                                .filter { it.isInnerBorder() }
//////                                .map { tunnel(it) }
//////                                .filterNotNull()
//////                                .filter { it.first.site().isEnvironment() }
//////                                .sortedBy { it.second }
//////                                .firstOrNull()?.first
////
////                        mine
////                                .filter { it.isInnerBorder() }
////                                .flatMap { it.neighbors() }
////                                .filter { it.isOuterBorder() && it.site().isEnvironment() }
////                                .minBy { distanceToEnemyGrid[it.y][it.x] }
////                                ?.let { distanceToEnemyGrid[it.y][it.x] = 0 }
////                    }
//                }

        if (enableDirectedWalk) {
            gameMap
                    .filter { it.isOuterBorder() && it.site().isEnvironment() }
                    .map { it to directedWalk(it) }
                    .forEach {
                        val (loc, value) = it
                        distanceToEnemyGrid[loc.y][loc.x] = value
                    }
        }


        gameMap.filter { it.isOuterBorder() }.sortedBy { distanceToEnemyGrid[it.y][it.x] }.take(20).forEach {
            walkGridFrom(mutableSetOf(it), mutableSetOf())
        }

        logGrid(distanceToEnemyGrid)
    }

    fun tunnel(loc: Location) =
            Direction.CARDINALS
                    .filter { loc.move(it).site().isEnvironment() }
                    .map {
                        var curr = loc.move(it)
                        var distance = curr.site().strength

                        while (curr.site().isEnvironment()) {
                            curr = curr.move(it)
                            if (curr.site().isEnvironment()) distance += curr.site().strength
                        }

                        if (curr.site().isOtherPlayer()) {
                            loc.move(it) to distance
                        } else null
                    }
                    .filterNotNull()
                    .sortedBy { it.second }
                    .firstOrNull()

    fun directedWalk(loc: Location): Int {
        val locToValue = mutableMapOf<Location, Double>()
        var minAvg: Double = loc.site().resource().toDouble()
        val queue = ArrayDeque<Location>()
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
                    .filter { it.site().isEnvironment() }
                    .forEach {
                        val nextValue = currAvg - ((currAvg - it.site().resource().toDouble()) / ((dist + 2)))
                        val currValue = locToValue.getOrPut(it, { nextValue })
                        if (nextValue < currValue) locToValue[it] = nextValue
                        queue.addLast(it)
                    }
        }

        return minAvg.toInt()
    }

    fun walkToDrop(loc: Location, grid: MutableList<MutableList<Int>>): Collection<Location> {
        var maxSeen = 0
        val startingSet = mutableSetOf(loc)
        val toVisit = mutableSetOf<Location>(loc)
        val visited = mutableSetOf<Location>()

        while (startingSet.isNotEmpty()) {
            val current = startingSet.first()
            startingSet.remove(current)
            if (current !in toVisit) {
                toVisit.add(current)

                current.neighbors()
                        .filter { grid[it.y][it.x] <= 2 }
                        .forEach { startingSet.add(it) }
            }
        }

        while (toVisit.isNotEmpty()) {
            val current = toVisit.first()
            toVisit.remove(current)
            if (current !in visited) {
                visited.add(current)

                current.neighbors()
                        .filter { !it.site().isEnvironment() && (grid[it.y][it.x] <= 2 || grid[it.y][it.x] >= grid[current.y][current.x]) }
                        .forEach { toVisit.add(it) }
            }
        }

        return visited
    }

    fun walkCombatGrid(distanceFromCombat: MutableList<MutableList<Int>>) {
        val openSet = mutableSetOf<Location>()
        openSet.addAll(gameMap.filter { it.site().isCombat() })

        val closedSet = mutableSetOf<Location>()

        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (!current.site().isEnvironment()) {
                    distanceFromCombat[current.y][current.x] =
                            Math.min(
                                    distanceFromCombat[current.y][current.x],
                                    1 + current.neighbors().map { distanceFromCombat[it.y][it.x] }.min()!!
                            )
                }

                current.neighbors()
                        .filter { !current.site().isEnvironment() }
                        .forEach { openSet.add(it) }
            }
        }

        logGrid(distanceFromCombat)
    }

    fun walkGridFrom(openSet: MutableSet<Location>, closedSet: MutableSet<Location>): Boolean {
        var changed = false
        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isMine() || current.site().isCombat()) {
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
                        .filterNot { it.site().isEnvironment() }
                        .forEach { openSet.add(it) }
            }
        }

        return changed
    }

    fun logGrid(grid: List<List<Int>>) {
        val builder = StringBuilder("\n")
        builder.append("     ")
        builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
        builder.append("\n")
        for (y in 0 until gameMap.height) {
            builder.append("$y".take(3).padEnd(4) + " ")
            builder.append(grid[y].map { "$it".take(3).padEnd(4) }.joinToString(" "))
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

                makeMove(move1)
                makeMove(move2)

                sources.put(source, move1.dir)
                sources.put(target, move2.dir)
                destinations.put(source, move2.dir)
                destinations.put(target, move1.dir)
            } else {
                var move = moveTowards(source, target)

                if (!blackout) {
                    val moveFromDestination = lastTurnMoves[target]
                    if (moveFromDestination != null && target.move(moveFromDestination) == source) {
                        move = Move(source, Direction.STILL)
                    }

                    if (nextMap.getSite(target).isMine() && gameMap.getSite(source).strength + nextMap.getSite(target).strength >= MAXIMUM_STRENGTH) {
                        move = Move(source, Direction.STILL)
                    }
                }

                makeMove(move)

                if (target != source) {
                    blackoutCells.add(source)
                    sources.put(source, move.dir)
                    destinations.put(target, move.dir)
                }

                updateNextMap(move)
            }
        }

        fun allCombos(locs: List<Location>): List<List<Location>> {
            val combos = mutableListOf<List<Location>>()

            (0 until locs.size).forEach { a ->
                combos.add(listOf(locs[a]))

                ((a + 1) until locs.size).forEach { b ->
                    combos.add(listOf(locs[a], locs[b]))

                    ((b + 1) until locs.size).forEach { c ->
                        combos.add(listOf(locs[a], locs[b], locs[c]))

                        ((c + 1) until locs.size).forEach { d ->
                            combos.add(listOf(locs[a], locs[b], locs[c], locs[d]))
                        }
                    }
                }
            }

            return combos
        }

        gameMap
                .filter { it.site().isMine() && it.site().strength > 0 }
                .sortedWith(compareBy({ -it.site().strength }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return
                    if (loc in sources) return@forEach

                    var target = loc.neighbors()
                            .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { -it.site().overkill() }))
                            .firstOrNull() ?: loc

                    if (target != loc) {
                        val targetSite = nextMap.getSite(target)

                        val valid =
                                if (targetSite.isCombat()) {
                                    // enemy space
                                    loc.site().strength > loc.site().production * 2 &&
                                            targetSite.strength + loc.site().strength < MAXIMUM_STRENGTH
                                } else if (targetSite.isEnvironment()) {
                                    // environment

                                    target.site().strength < loc.site().strength
                                } else {
                                    // mine
                                    loc.site().strength > growthLimit(target) &&
                                            (targetSite.strength + loc.site().strength < MAXIMUM_STRENGTH || target.swappable(loc))
                                }

                        if (!valid || target in blackoutCells) target = loc
                    }

                    finalizeMove(loc, target, target.site().isCombat())
                }

        gameMap
                .filter { it.isOuterBorder() && nextMap.getSite(it).isEnvironment() }
                .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { -it.site().overkill() }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val wouldAttack = loc.neighbors()
                            .filter { it.site().isMine() && it !in sources && it.site().strength > it.site().production * 2 }
                            .filter { distanceToEnemyGrid[it.y][it.x] > distanceToEnemyGrid[loc.y][loc.x] }

                    allCombos(wouldAttack)
                            .sortedWith(compareBy({ it.size }, { it.map { it.site().strength }.sum() }))
                            .filter {
                                val sum = it.sumBy { it.site().strength }
                                sum > loc.site().strength && sum < MAXIMUM_STRENGTH
                            }
                            .firstOrNull()
                            ?.forEach {
                                finalizeMove(it, loc, true)
                            }
                }

//        val still255 = allMoves.filter { it.key.site().strength == 255 && it.value == Direction.STILL }
//        if (still255.isNotEmpty()) {
//            val closest = gameMap
//                    .filter { it.site().isCombat() }
//                    .map { cloc ->
//                        cloc to still255.minBy { gameMap.getDistance(cloc, it.key) }
//                    }
//
//            closest.forEach {
//                val source = it.second?.key
//                if (source != null) {
//                    val target = source.neighbors()
//                            .filter { it.site().isEnvironment() }
//                            .sortedByDescending { it.site().overkill() }
//                            .firstOrNull()
//
//                    if (target != null) {
//                        finalizeMove(source, target, true)
//                    }
//                }
//                // logger.info("combat ${it.first} nearest ${it.second?.key}")
//            }
//        }

        stillMax = allMoves.filter { it.key.site().strength == 255 && it.value == Direction.STILL }.size
    }

    fun makeMove(move: Move) {
        allMoves.put(move.loc, move.dir)
    }

    fun sendMoves() = allMoves.map { Move(it.key, it.value) }

    // EXTENSIONS METHODS

    fun <T> List<T>.shuffle(): List<T> {
        val copy = this.toMutableList()
        ((copy.size - 1) downTo 1).forEach { i ->
            val j = Random().nextInt(i + 1)
            val temp = copy[i]
            copy[i] = copy[j]
            copy[j] = temp
        }
        return copy.toList()
    }

    // SITE

    fun Site.isOtherPlayer() = this.owner != id && this.owner > 0

    fun Site.isEnvironment() = this.owner == 0 && this.strength > 0

    fun Site.isMine() = this.owner == id

    fun Site.isCombat() = this.owner == 0 && this.strength == 0

    fun Site.overkill() =
            if (this.isEnvironment()) {
                -distanceToEnemyGrid[this.loc.y][this.loc.x]
            } else {
                this.loc.neighbors()
                        .map {
                            if (it.site().isOtherPlayer()) it.site().strength + it.site().production
                            else if (nextMap.getSite(it).isMine()) it.allNeighborsWithin(2)
                                    .filter { it.site().isMine() }
                                    .map { -nextMap.getSite(it).strength }
                                    .sum()
                            else 0
                        }.sum()
            }

    enum class ResourceStrategy {
        Average,
        Pure
    }

    fun Site.averageResource() = if (!this.isMine()) {
        (this.loc.neighbors()
                .filter { it.site().isEnvironment() && it.site().production > 0 }
                .map {
                    (it.site().strength / (it.site().production + stillMax).toDouble())
                }
                .average() + (this.strength / (this.production + stillMax).toDouble()))
                .toInt() / 2
    }
    else 9999

    fun Site.pureResource() = if (!this.isMine()) {
        if (this.production == 0) Int.MAX_VALUE
        else (this.strength / (this.production + stillMax).toDouble()).toInt()
    }
    else 9999

    fun Site.resource() = when(resourceStrategy) {
        ResourceStrategy.Pure -> this.pureResource()
        ResourceStrategy.Average -> this.averageResource()
    }

    // LOCATION

    fun Location.isInnerBorder() =
            this.site().isMine() &&
                    this.neighbors().any { !it.site().isMine() }

    fun Location.isOuterBorder() =
            !this.site().isMine() &&
                    this.neighbors().any { it.site().isMine() }

    fun Location.neighbors() = Direction.CARDINALS.map { this.move(it) }

    fun Location.neighborsAndSelf() = Direction.DIRECTIONS.map { this.move(it) }

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

    fun log2(num: Double) = Math.log10(num) / Math.log10(2.0)

    enum class GrowthStrategy {
        Dynamic,
        DynamicWithMinimum,
        Static,
        StaticWithMinumum
    }

    fun growthLimit(loc: Location) = when (growthStrategy) {
        GrowthStrategy.Dynamic -> loc.site().production * dynamicMultiplier(loc)
        GrowthStrategy.DynamicWithMinimum -> Math.max(loc.site().production * dynamicMultiplier(loc), if (madeContact) MINIMUM_STRENGTH * 2 else 0)
        GrowthStrategy.Static -> loc.site().production * 5
        GrowthStrategy.StaticWithMinumum -> Math.max(loc.site().production * 5, MINIMUM_STRENGTH * 2)
    }

    fun dynamicMultiplier(loc: Location) =
            Math.max(1, Math.min(5.0, log2(distanceToEnemyGrid[loc.y][loc.x].toDouble() * if (madeContact) 3.0 else 1.5)).toInt())
}
