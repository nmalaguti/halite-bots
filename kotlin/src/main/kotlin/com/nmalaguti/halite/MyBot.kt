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
            Networking.sendFrame(allMoves)
        }
    }

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID
        points = permutations(gameMap)
        numPlayers = gameMap.groupBy { gameMap.getSite(it).owner }.keys.size

        logger.info("id: $id")

        // 15 seconds to test out which behavior will work best
        preprocess()

        logger.info("resource strategy: $resourceStrategy")
        logger.info("growth strategy: $growthStrategy")

        turn = 0
        allMoves = mutableSetOf()
        stillMax = 0
        madeContact = false

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

            turn = 0
            allMoves = mutableSetOf()
            stillMax = 0
            madeContact = false
            gameMap = GameMap(initialGameMap)

            (0 until 50).forEach {
                if (System.currentTimeMillis() - preprocessStart > 14000) return@strats

                val startGameMap = GameMap(gameMap)
                gameLoop(startGameMap)
                gameMap = processFrame(startGameMap, allMoves, numPlayers)
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

        lastTurnMoves = allMoves.associateBy { it.loc }
        playerStats = playerStats()

        // reset all moves
        allMoves = mutableSetOf()
        territory = points.filter { it.site().isMine() }.size
        border = points.filter { it.isInnerBorder() }.size
        if (points.any { it.site().isEnvironment() && it.site().strength == 0 && it.neighbors().any { it.site().isMine() }}) {
            madeContact = true
        }

        buildDistanceToEnemyGrid()

        makeBattleMoves()

        stillMax = allMoves.filter { it.loc.site().strength == 255 && it.dir == Direction.STILL }.size

        logger.info("stillMax: $stillMax")
        logger.info("additive: ${(territory / border.toDouble()).toInt()}")
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

        val distanceFromCombat = mutableListOf<MutableList<Int>>()

        for (y in 0 until gameMap.height) {
            val row = mutableListOf<Int>()
            for (x in 0 until gameMap.width) {
                val loc = Location(x, y)
                if (loc.site().isEnvironment() && loc.site().strength == 0) row.add(0)
                else row.add(9999)
            }
            distanceFromCombat.add(row)
        }

        walkCombatGrid(distanceFromCombat)

        val counted = mutableSetOf<Location>()

        points
                .filter { it.site().isEnvironment() && it.site().strength == 0 }
                .forEach {
                    if (it in counted) return@forEach

                    val sphereOfInfluence = walkToDrop(it, distanceFromCombat)

                    val count255 = sphereOfInfluence.filter { it.site().strength == 255 }.size

                    distanceToEnemyGrid[it.y][it.x] = count255
                    it.neighbors()
                            .filter { it.site().isEnvironment() && it.site().strength > 0 }
                            .forEach { distanceToEnemyGrid[it.y][it.x] -= count255 }
                }

        if (enableDirectedWalk) {
            points
                    .filter { it.isOuterBorder() && it.site().isEnvironment() && it.site().strength > 0 }
                    .map { it to directedWalk(it) }
                    .forEach {
                        val (loc, value) = it
                        distanceToEnemyGrid[loc.y][loc.x] = value
                    }
        }


        points.filter { it.isOuterBorder() }.sortedBy { distanceToEnemyGrid[it.y][it.x] }.take(20).forEach {
            walkGridFrom(mutableSetOf(it), mutableSetOf())
        }

        logGrid(distanceToEnemyGrid)
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
                    .filter { it.site().isEnvironment() && it.site().strength > 0 }
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
        val toVisit = mutableSetOf(loc)
        val visited = mutableSetOf<Location>()

        while (toVisit.isNotEmpty()) {
            val current = toVisit.first()
            toVisit.remove(current)
            if (current !in visited) {
                visited.add(current)

                if (grid[current.y][current.x] > maxSeen) maxSeen = grid[current.y][current.x]

                current.neighbors()
                        .filter { it.site().isMine() && grid[it.y][it.x] >= maxSeen }
                        .forEach { toVisit.add(it) }
            }
        }

        return visited
    }

    fun walkCombatGrid(distanceFromCombat: MutableList<MutableList<Int>>) {
        val openSet = mutableSetOf<Location>()
        openSet.addAll(gameMap.filter { it.site().isEnvironment() && it.site().strength == 0 })

        val closedSet = mutableSetOf<Location>()

        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isMine()) {
                    distanceFromCombat[current.y][current.x] =
                            Math.min(
                                    distanceFromCombat[current.y][current.x],
                                    1 + current.neighbors().map { distanceFromCombat[it.y][it.x] }.min()!!
                            )
                }

                current.neighbors()
                        .filter { it.site().isMine() }
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
                                if (targetSite.isEnvironment() && targetSite.strength == 0) {
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

                    finalizeMove(loc, target, target.site().isEnvironment() && target.site().strength == 0)
                }

        points
                .filter { it.isOuterBorder() && nextMap.getSite(it).isEnvironment() && nextMap.getSite(it).strength > 0 }
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
    }

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

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isMine() = this.owner == id

    fun Site.overkill() =
            if (this.isEnvironment() && this.strength > 0) {
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
                .filter { it.site().isEnvironment() && it.site().strength > 0 && it.site().production > 0 }
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

    fun Location.allNeighborsWithin(distance: Int) = points.filter { gameMap.getDistance(it, this) <= distance }

    // GAMEMAP

    fun playerStats() = points
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

fun processFrame(initialMap: GameMap, submittedMoves: Collection<Move>, numPlayers: Int): GameMap {
    val gameMap = GameMap(initialMap)
    val width = gameMap.width
    val height = gameMap.height

    val moves = (0 until height)
            .map {
                (0 until width).map { Direction.STILL }.toMutableList()
            }
            .toMutableList()

    submittedMoves.forEach {
        moves[it.loc.y][it.loc.x] = it.dir
    }

    val pieces = (0 until numPlayers)
            .map {
                (0 until height)
                        .map {
                            (0 until width).map { null as Int? }.toMutableList()
                        }
                        .toMutableList()
            }
            .toMutableList()

    (0 until numPlayers).forEach {
        pieces.add(mutableListOf())
    }

    (0 until height).forEach y@ { y ->
        (0 until width).forEach x@ { x ->
            val direction = moves[y][x]
            val cell = gameMap.contents[y][x]
            val player = cell.owner - 1
            val production = cell.production

            if (cell.owner == 0) return@x

            if (direction == Direction.STILL) {
                if (cell.strength + production <= 255) cell.strength += cell.production
                else cell.strength = 255
            }

            val newLoc = gameMap.getLocation(Location(x, y), direction)
            if (pieces[player][newLoc.y][newLoc.x] != null) {
                if (pieces[player][newLoc.y][newLoc.x]!! + cell.strength <= 255)
                    pieces[player][newLoc.y][newLoc.x] = pieces[player][newLoc.y][newLoc.x]?.plus(cell.strength)
                else pieces[player][newLoc.y][newLoc.x] = 255
            } else {
                pieces[player][newLoc.y][newLoc.x] = cell.strength
            }

            if (pieces[player][y][x] == null) {
                pieces[player][y][x] = 0
            }

            gameMap.contents[y][x] = Site(0, gameMap.contents[y][x].production, 0, Location(x, y))
        }
    }

    val toInjure = (0 until numPlayers)
            .map {
                (0 until height)
                        .map {
                            (0 until width).map { null as Int? }.toMutableList()
                        }
                        .toMutableList()
            }
            .toMutableList()

    val injureMap = (0 until height)
            .map {
                (0 until width).map { 0 }.toMutableList()
            }
            .toMutableList()


    (0 until height).forEach y@ { y ->
        (0 until width).forEach x@ { x ->
            (0 until numPlayers).forEach p@ { p ->
                if (pieces[p][y][x] != null) {
                    (0 until numPlayers).forEach q@ { q ->
                        if (p != q) {
                            Direction.DIRECTIONS.forEach { dir ->
                                val loc = gameMap.getLocation(Location(x, y), dir)

                                if (pieces[q][loc.y][loc.x] != null) {
                                    if (toInjure[q][loc.y][loc.x] != null) {
                                        toInjure[q][loc.y][loc.x] = toInjure[q][loc.y][loc.x]?.plus(pieces[p][y][x]!!)
                                    } else {
                                        toInjure[q][loc.y][loc.x] = pieces[p][y][x]
                                    }
                                }
                            }
                        }
                    }

                    if (gameMap.contents[y][x].strength > 0) {
                        if (toInjure[p][y][x] != null) {
                            toInjure[p][y][x] = toInjure[p][y][x]?.plus(gameMap.contents[y][x].strength)
                        } else {
                            toInjure[p][y][x] = gameMap.contents[y][x].strength
                        }
                        injureMap[y][x] += pieces[p][y][x]!!
                    }
                }
            }
        }
    }

    (0 until numPlayers).forEach p@ { p ->
        (0 until height).forEach y@ { y ->
            (0 until width).forEach x@ { x ->
                if (toInjure[p][y][x] != null) {
                    if (toInjure[p][y][x]!! >= pieces[p][y][x] ?: 0) {
                        pieces[p][y][x] = null
                    } else {
                        pieces[p][y][x] = pieces[p][y][x]?.minus(toInjure[p][y][x]!!)
                    }
                }
            }
        }
    }

    (0 until height).forEach y@ { y ->
        (0 until width).forEach x@ { x ->
            if (gameMap.contents[y][x].strength < injureMap[y][x]) {
                gameMap.contents[y][x].strength = 0
            } else {
                gameMap.contents[y][x].strength -= injureMap[y][x]
            }
            gameMap.contents[y][x].owner = 0
        }
    }

    (0 until numPlayers).forEach p@ { p ->
        (0 until height).forEach y@ { y ->
            (0 until width).forEach x@ { x ->
                if (pieces[p][y][x] != null) {
                    gameMap.contents[y][x].owner = p + 1
                    gameMap.contents[y][x].strength = pieces[p][y][x]!!
                }
            }
        }
    }

    return GameMap(gameMap)
}
