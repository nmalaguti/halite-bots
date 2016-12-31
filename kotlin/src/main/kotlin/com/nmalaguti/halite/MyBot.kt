package com.nmalaguti.halite

import java.util.*
import kotlin.comparisons.compareBy

val BOT_NAME = "MyTravelerBot"
val MAXIMUM_TIME = 940 // ms
val PI4 = Math.PI / 4
val MINIMUM_STRENGTH = 15
val MAXIMUM_STRENGTH = 256
val DEBUG_TIE_BREAKERS = false

object MyBot {
    lateinit var gameMap: GameMap
    lateinit var nextMap: GameMap
    var id: Int = 0
    var turn: Int = 0
    var allMoves = mutableSetOf<Move>()
    var start = System.currentTimeMillis()
    var lastTurnMoves: Map<Location, Move> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()
    var distanceToEnemyGrid = mutableListOf<MutableList<Int>>()
    var cellsToEnemyGrid = mutableListOf<MutableList<Int>>()
    var cellsToBorderGrid = mutableListOf<MutableList<Int>>()
    var directedGrid = mapOf<Location, Pair<Int, Int>>()
    var stillMax: Int = 0
    var stillMaxCells = setOf<Location>()
    var madeContact: Boolean = false
    var numPlayers: Int = 0
    var numConnectedPlayers: Int = 0
    var hotSpots = setOf<Location>()
    var hotSpotsGrid = mutableListOf<MutableList<Int>>()
    var useHotSpots = true
    var localProduction: Double = 0.0

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID

        logger.info("id: $id")

        hotSpots = findHotSpots()
        hotSpotsGrid = mutableListOf<MutableList<Int>>()
        for (y in 0 until gameMap.height) {
            val row = mutableListOf<Int>()
            for (x in 0 until gameMap.width) {
                if (Location(x, y) in hotSpots) {
                    row.add(0)
                } else {
                    row.add(9999)
                }
            }
            hotSpotsGrid.add(row)
        }

        useHotSpots = hotSpots.isNotEmpty()

        if (useHotSpots) {
            walkHotSpots(hotSpots.toMutableSet(), mutableSetOf())
            logGrid(hotSpotsGrid)
        }

        val myLocation = gameMap.find { it.site().isMine() }
        if (useHotSpots && myLocation != null) {
            var distance = 0
            var count = hotSpots.size

            while (count > 0) {
                distance++
                count -= (4 * distance)
            }

            localProduction = myLocation.allNeighborsWithin(Math.min(4, distance)).map { it.site().production }.average()
            val hotSpotProduction = hotSpots.map { it.site().production }.average()

            logger.info("distance: $distance")
            logger.info("localProduction: $localProduction")
            logger.info("hotSpotProduction: $hotSpotProduction")

            if (hotSpotProduction < localProduction) useHotSpots = false
        }

        logger.info("using hot spots: $useHotSpots")

        Networking.sendInit(BOT_NAME)
    }

    fun findHotSpots(): Set<Location> {
        // build list of siding window images

        val grid = gameMap.contents.plus(gameMap.contents)

        logger.info("average: ${gameMap.map { it.site().resource() }.average()}")

        (Math.min(15, Math.min(gameMap.width, gameMap.height)) downTo 5).forEach { windowSize ->
            (2..5).forEach { minimumValue ->
                val hotSpots = (0 until gameMap.height)
                        .flatMap { y ->
                            (0 until gameMap.width)
                                    .flatMap { x ->
                                        val window = grid
                                                .subList(y, y + windowSize)
                                                .map { it.plus(it).subList(x, x + windowSize) }
                                                .flatten()

                                        val avg = window.map { (if (it.isMine()) 0 else it.resource()) }.average()
                                        window.filter { (if (it.isMine()) 0 else it.resource()) < minimumValue }.map { it.loc to avg }
                                    }
                        }
                        .filter { it.second < minimumValue }
                        .map {
                            it.first
                        }
                        .toSet()

                if (hotSpots.isNotEmpty()) {
                    logger.info("windowSize: $windowSize, minimumValue: $minimumValue, num: ${hotSpots.size}")
                    return hotSpots
                }
            }
        }

        return emptySet()
    }

    fun walkHotSpots(openSet: MutableSet<Location>, closedSet: MutableSet<Location>) {
        while (openSet.isNotEmpty()) {
            val current = openSet.minBy { it.neighborsAndSelf().map { hotSpotsGrid[it.y][it.x] }.min()!! }!!
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isEnvironment() && current.site().strength > 0) {
                    hotSpotsGrid[current.y][current.x] = Math.min(
                            hotSpotsGrid[current.y][current.x],
                            current.site().resource() +
                                    current.neighbors().map { hotSpotsGrid[it.y][it.x] }.min()!!)
                }

                current.neighbors()
                        .filter { it.site().isEnvironment() && it.site().strength > 0 }
                        .forEach { openSet.add(it) }
            }
        }
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

            numPlayers = gameMap.groupBy { it.site().owner }.keys.filter { it != 0 }.size
            numConnectedPlayers = connectedPlayers().size
            madeContact = numConnectedPlayers > 1

            logger.info("numPlayers: $numPlayers")
            logger.info("numConnectedPlayers: $numConnectedPlayers")
            logger.info("madeContact: $madeContact")

            // reset all moves
            allMoves = mutableSetOf()

            if (useHotSpots && hotSpots.any { it.site().isMine() }) useHotSpots = false
            logger.info("useHotSpots: $useHotSpots")

            buildDistanceToEnemyGrid()

            makeBattleMoves()

            logger.info("stillMax: $stillMax")

            endGameLoop()
        }
    }

    // MOVE LOGIC

    fun connectedPlayers(): Set<Int> {
        val connectedCells = visitNotEnvironment(gameMap.filter { it.isInnerBorder() }.toMutableSet())
        return connectedCells.groupBy { it.site().owner }.filterNot { it.key == 0 }.keys
    }

    fun visitNotEnvironment(openSet: MutableSet<Location>): MutableSet<Location> {
        val closedSet = mutableSetOf<Location>()
        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                current.neighbors()
                        .filterNot { it.site().isEnvironment() && it.site().strength > 0 }
                        .forEach { openSet.add(it) }
            }
        }

        return closedSet
    }

    fun buildDistanceToEnemyGrid() {
        cellsToEnemyGrid = mutableListOf<MutableList<Int>>()
        for (y in 0 until gameMap.height) {
            val row = mutableListOf<Int>()
            for (x in 0 until gameMap.width) {
                row.add(9999)
            }
            cellsToEnemyGrid.add(row)
        }

        walkCellsGridEnemies(gameMap.filter { it.site().isOtherPlayer() }.toMutableSet(), mutableSetOf())

        logGrid(cellsToEnemyGrid)

        cellsToBorderGrid = mutableListOf<MutableList<Int>>()
        for (y in 0 until gameMap.height) {
            val row = mutableListOf<Int>()
            for (x in 0 until gameMap.width) {
                row.add(9999)
            }
            cellsToBorderGrid.add(row)
        }

        walkCellsGridBorder(gameMap.filter { it.isInnerBorder() }.toMutableSet(), mutableSetOf())

        logGrid(cellsToBorderGrid)

        if (!madeContact && useHotSpots && gameMap.filter { it.isOuterBorder() }.map { hotSpotsGrid[it.y][it.x] }.min() ?: 0 < (1000 / localProduction)) {
            distanceToEnemyGrid = hotSpotsGrid
        } else {
            distanceToEnemyGrid = mutableListOf<MutableList<Int>>()
            for (y in 0 until gameMap.height) {
                val row = mutableListOf<Int>()
                for (x in 0 until gameMap.width) {
                    row.add(Location(x, y).site().resource())
                }
                distanceToEnemyGrid.add(row)
            }

            directedGrid = gameMap
                    .filter { it.isOuterBorder() && it.site().isEnvironment() && it.site().strength > 0 }
                    .map { it to directedWalk(it) }
                    .toMap()

            directedGrid.forEach {
                val (loc, value) = it

                if (value.second <= distanceToEnemyGrid[loc.y][loc.x]) {
                    distanceToEnemyGrid[loc.y][loc.x] = value.second
                }
            }


            if (stillMax > 1) {
                gameMap
                        .filter { it.site().isEnvironment() && it.site().strength == 0 }
                        .forEach { loc ->
                            val owners = loc.neighbors()

                            val combatCells = owners.filter { it.site().isEnvironment() && it.site().strength == 0 }
                            val environmentCells = owners.filter { it.site().isEnvironment() && it.site().strength > 0 }
                            val otherPlayerCells = owners.filter { it.site().isOtherPlayer() }
                            val myCells = owners.filter { it.site().isMine() }

                            if (myCells.isNotEmpty() && otherPlayerCells.isNotEmpty() && combatCells.isEmpty() && environmentCells.size == 2) {
                                if (otherPlayerCells.map { it.site().owner }.all { playerStats[it]?.strength ?: 0 < playerStats[id]?.strength ?: 0 }) {
                                    environmentCells.forEach {
                                        distanceToEnemyGrid[it.y][it.x] = 0
                                    }
                                }
                            }
                        }
            }
        }

        // final step

        gameMap
                .filter { it.isOuterBorder() }
                .sortedBy { distanceToEnemyGrid[it.y][it.x] }
                .take(20)
                .forEach {
                    walkGridFrom(mutableSetOf(it), mutableSetOf())
                }

        logGrid(distanceToEnemyGrid)
    }

    fun directedWalk(loc: Location): Pair<Int, Int> {
        val locToValue = mutableMapOf<Location, Pair<Double, Double>>()
        var minAvg: Pair<Double, Double> = 0.0 to loc.site().resource().toDouble()
        val queue = ArrayDeque<Location>()
        queue.addFirst(loc)
        locToValue[loc] = minAvg
        val visited = mutableSetOf<Location>()

        while (queue.isNotEmpty()) {
            val currLoc = queue.removeFirst()

            if (currLoc in visited) continue
            visited.add(currLoc)

            val dist = gameMap.getDistance(currLoc, loc)

            if (dist > Math.min(gameMap.width, gameMap.height) / (4 * if (madeContact) 2 else 1)) continue

            val currAvg = locToValue[currLoc] ?: minAvg

            if (currAvg.second < minAvg.second) minAvg = currAvg

            currLoc.neighbors()
                    .filter { it.site().isEnvironment() && it.site().strength > 0 }
                    .forEach {
                        val nextValue = currAvg.second - ((currAvg.second - it.site().resource().toDouble()) / ((dist + 3)))
                        val currValue = locToValue.getOrPut(it, { dist to nextValue }).second
                        if (nextValue < currValue) locToValue[it] = dist to nextValue
                        queue.addLast(it)
                    }
        }

        return minAvg.first.toInt() to minAvg.second.toInt()
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
                                    1 +
                                            current.neighbors().map { distanceToEnemyGrid[it.y][it.x] }.min()!! +
                                            if (madeContact)
                                                (Math.max(0.0, Math.log(current.site().production.toDouble() / Math.log(2.0))).toInt())
                                            else 0
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

    fun walkCellsGridEnemies(openSet: MutableSet<Location>, closedSet: MutableSet<Location>) {
        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isOtherPlayer()) {
                    cellsToEnemyGrid[current.y][current.x] = 0
                } else {
                    cellsToEnemyGrid[current.y][current.x] = Math.min(
                            cellsToEnemyGrid[current.y][current.x],
                            1 + current.neighbors().map { cellsToEnemyGrid[it.y][it.x] }.min()!!)
                }

                current.neighbors()
                        .filterNot { it.site().isEnvironment() && it.site().strength > 0 }
                        .forEach { openSet.add(it) }
            }
        }
    }

    fun walkCellsGridBorder(openSet: MutableSet<Location>, closedSet: MutableSet<Location>) {
        while (openSet.isNotEmpty()) {
            val current = openSet.first()
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.isInnerBorder()) {
                    cellsToBorderGrid[current.y][current.x] = 0
                } else {
                    cellsToBorderGrid[current.y][current.x] = Math.min(
                            cellsToBorderGrid[current.y][current.x],
                            1 + current.neighbors().map { cellsToBorderGrid[it.y][it.x] }.min()!!)
                }

                current.neighbors()
                        .filter { it.site().isMine() }
                        .forEach { openSet.add(it) }
            }
        }
    }

    fun logGrid(grid: List<List<Int?>>) {
        val builder = StringBuilder("\n")
        builder.append("     ")
        builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
        builder.append("\n")
        for (y in 0 until gameMap.height) {
            builder.append("$y".take(3).padEnd(4) + " ")
            builder.append(grid[y].mapIndexed { x, it ->
                val value: Any? = if (Location(x, y).site().isMine() && it != null) it.toDouble() else it
                "$value".take(3).padEnd(4) }.joinToString(" ")
            )
            builder.append(" " + "$y".take(3).padEnd(4))
            builder.append("\n")
        }
        builder.append("     ")
        builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
        builder.append("\n")

        logger.info(builder.toString())
    }

    fun makeBattleMoves() {
        val battleBlackout = mutableSetOf<Location>()
        val blackoutCells = mutableSetOf<Location>()

        val sources = mutableMapOf<Location, Direction>()
        val destinations = mutableSetOf<Location>()

        val bestTargetStrength = gameMap
                .filter { it.isOuterBorder() }
                .filter { it.site().isEnvironment() && it.site().strength > 0 }
                .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { it.site().strength }))
                .firstOrNull()
                ?.site()
                ?.strength ?: 255

        val myProduction = playerStats[id]?.production ?: 1

        val minimumStrength = (Math.min(10, myProduction / bestTargetStrength) * MINIMUM_STRENGTH / 5) + stillMax

        logger.info("minimum strength: $minimumStrength")

        fun Location.swappable(source: Location) =
                this != source &&
                        this.site().isMine() &&
                        this !in sources &&
                        this !in destinations &&
                        ((source.site().strength == 255 && this.site().strength < 255) ||
                                this.site().strength + 15 < source.site().strength)

        fun updateNextMap(move: Move) {
            val source = move.loc
            val target = move.loc.move(move.dir)

            if (source in sources) logger.warning("Source $source in sources")

            val originSite = gameMap.getSite(source)
            val targetSite = gameMap.getSite(target)
            val startSite = nextMap.getSite(source)
            val destinationSite = nextMap.getSite(target)

            if (startSite.owner == destinationSite.owner) {
                if (move.dir == Direction.STILL) {
                    logger.warning("Made move STILL at $source")
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

        fun finalizeMove(source: Location, target: Location, addToBattleBlackout: Boolean, preventSwaps: Boolean) {
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

                destinations.add(source)
                destinations.add(target)
            } else if (source != target) {
                val move = moveTowards(source, target)

                if (target in battleBlackout || (target in blackoutCells && source.site().strength != 255)) logger.warning("target $target in blackout cells")

                if (preventSwaps) {
                    val moveFromDestination = lastTurnMoves[move.loc.move(move.dir)]
                    if (moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == move.loc) {
                        return
                    }
                }

                if (nextMap.getSite(target).isMine() && gameMap.getSite(source).strength + nextMap.getSite(target).strength >= MAXIMUM_STRENGTH) {
                    logger.warning("Move from $source to $target causes cap loss")
                }

                updateNextMap(move)

                allMoves.add(move)

                if (addToBattleBlackout) battleBlackout.add(source)
                blackoutCells.add(source)

                sources.put(source, move.dir)
                destinations.add(target)
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

        /////////////////////////////////////////////////////

        gameMap
                .filter {
                    it.site().isMine() &&
                            it.cornerNeighbors().any { it.site().isOtherPlayer() && it.site().strength == 255 } &&
                            it.site().strength == 255
                }
                .forEach { loc ->
                    if (loc in sources) return@forEach

                    loc.cornerNeighbors()
                            .filter { it !in sources && it.site().isMine() && it.site().strength == 255 && it.neighbors().any { it.site().isEnvironment() && it.site().strength == 0 } }
                            .forEach { mine ->
                                // move away if possible
                                mine.neighbors()
                                        .filter { nextMap.getSite(it).isMine() && mine.site().strength + nextMap.getSite(it).strength < MAXIMUM_STRENGTH }
                                        .sortedBy { nextMap.getSite(it).strength }
                                        .firstOrNull()
                                        ?.let { target ->
                                            finalizeMove(mine, target, true, false)
                                        }
                            }
                }

        gameMap
                .filter { it.site().isMine() && it !in sources && it.site().strength > 0 }
                .filter { it.neighbors().any { it.site().isEnvironment() && it.site().strength == 0 } }
                .sortedWith(compareBy({ -it.site().strength }, { cellsToEnemyGrid[it.y][it.x] }))
                .forEach { loc ->
                    // on the edge of battle
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    if (loc in sources) return@forEach

                    val target =
                            if (loc.site().strength < loc.site().production * 2 || loc.site().strength < MINIMUM_STRENGTH) loc
                            else loc.neighbors()
                                    .filter { it.site().isEnvironment() && it.site().strength == 0 }
                                    .filter { it !in battleBlackout }
                                    .filter {
                                        nextMap.getSite(it).strength + loc.site().strength < MAXIMUM_STRENGTH ||
                                                it.swappable(loc)
                                    }
                                    .sortedWith(compareBy(
                                            { -it.site().overkill() },
                                            { -it.neighbors().filter { nextMap.getSite(it).isOtherPlayer() }.size },
                                            { -it.site().production }
                                    ))
                                    .let {
                                        if (DEBUG_TIE_BREAKERS) {
                                            it
                                                    .map {
                                                        it to listOf(
                                                                -it.site().overkill(),
                                                                -it.neighbors().filter { nextMap.getSite(it).isOtherPlayer() }.size,
                                                                -it.site().production
                                                        )
                                                    }
                                                    .groupBy { it.second }
                                                    .let {
                                                        if (it.any { it.value.size > 1 }) {
                                                            val builder = StringBuilder()

                                                            builder.appendln("combat tie breaker")
                                                            it.values.flatten().forEach {
                                                                builder.appendln("${it.first} [${it.first.site()}]: ${it.second.joinToString(", ")}")
                                                            }
                                                            logger.info(builder.toString())
                                                        }
                                                    }
                                        }

                                        it
                                    }
                                    .firstOrNull() ?: loc

                    finalizeMove(loc, target, true, false)

                    if (loc.site().strength == 255) {
                        target.neighbors().filterNot { it.site().isEnvironment() && it.site().strength > 0 }.forEach {
                            if (it.neighbors().none { nextMap.getSite(it).isCombat() || it.site().isOtherPlayer() } && it.cornerNeighbors().any { it.site().isOtherPlayer() && it.site().strength == 255 }) {
                                battleBlackout.add(it)
                            }
                        }
                    }
                }

        gameMap
                .filter { it.site().isMine() && it !in sources }
                .sortedWith(compareBy({ cellsToEnemyGrid[it.y][it.x] }, { distanceToEnemyGrid[it.y][it.x] }, { -it.site().strength }, { it.neighbors().filterNot { it.site().isMine() }.size }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val target = loc.neighbors()
                            .filter { it !in battleBlackout }
                            .filter { it !in blackoutCells || loc.site().strength == 255 }
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
                                    loc.site().strength > Math.min(128, Math.max(loc.site().production * (Math.max(0, cellsToBorderGrid[loc.y][loc.x] - 2) + 5), minimumStrength)) &&
                                            (nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH || it.swappable(loc))
                                }
                            }
                            .sortedWith(compareBy(
                                    { distanceToEnemyGrid[it.y][it.x] },
                                    { if (it in directedGrid) directedGrid[it]!!.first else 0 },
                                    { if (madeContact) 0 else -it.site().production },
                                    { if (it.site().isEnvironment() && it.site().strength > 0) it.site().strength / Math.max(1, it.site().production) else 0 },
                                    { if (it.site().isEnvironment() && it.site().strength > 0) -it.site().production else 0 },
                                    { if (it.site().isEnvironment() && it.site().strength > 0) it.site().strength else 0 },
                                    { -it.site().overkill() },
                                    { -it.neighbors().filterNot { nextMap.getSite(it).isMine() }.size }
                            ))
                            .let {
                                if (DEBUG_TIE_BREAKERS) {
                                    it
                                            .map {
                                                it to listOf(
                                                        distanceToEnemyGrid[it.y][it.x],
                                                        if (it in directedGrid) directedGrid[it]!!.first else 0,
                                                        if (madeContact) 0 else -it.site().production,
                                                        if (it.site().isEnvironment() && it.site().strength > 0) it.site().strength / Math.max(1, it.site().production) else 0,
                                                        if (it.site().isEnvironment() && it.site().strength > 0) -it.site().production else 0,
                                                        if (it.site().isEnvironment() && it.site().strength > 0) it.site().strength else 0,
                                                        -it.site().overkill(),
                                                        -it.neighbors().filterNot { nextMap.getSite(it).isMine() }.size
                                                )
                                            }
                                            .groupBy { it.second }
                                            .let {
                                                if (it.any { it.value.size > 1 }) {
                                                    val builder = StringBuilder()

                                                    builder.appendln("tie breaker")
                                                    it.values.flatten().forEach {
                                                        builder.appendln("${it.first} [${it.first.site()}]: ${it.second.joinToString(", ")}")
                                                    }
                                                    logger.info(builder.toString())
                                                }
                                            }
                                }

                                it
                            }
                            .firstOrNull()

                    if (target != null) {
                        if (target in blackoutCells && loc.site().strength < 255) logger.warning("Want to move $loc to $target but it is a blackout cell and source doesn't have 255 strength")
                        finalizeMove(loc, target, false, true)
                    }
                }

        gameMap
                .filter {
                    it.isOuterBorder() &&
                            nextMap.getSite(it).isEnvironment() &&
                            nextMap.getSite(it).strength > 0 &&
                            it !in battleBlackout &&
                            it !in blackoutCells
                }
                .sortedWith(compareBy({ distanceToEnemyGrid[it.y][it.x] }, { -it.site().overkill() }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val wouldAttack = loc.neighbors()
                            .filter {
                                it.site().isMine() &&
                                        it !in sources &&
                                        it.site().strength > Math.max(it.site().production * 5, minimumStrength)
                            }
                            .filter { distanceToEnemyGrid[it.y][it.x] > distanceToEnemyGrid[loc.y][loc.x] }

                    allCombos(wouldAttack)
                            .sortedWith(compareBy({ it.size }, { it.map { it.site().strength }.sum() }))
                            .filter {
                                val sum = it.sumBy { it.site().strength }
                                sum > loc.site().strength && sum < MAXIMUM_STRENGTH
                            }
                            .firstOrNull()
                            ?.forEach {
                                finalizeMove(it, loc, false, true)
                            }
                }

        stillMaxCells = gameMap
                .filter { it.site().isMine() && it !in sources && it.site().strength == 255 }
                .toSet()
        stillMax = stillMaxCells.size
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isCombat() = this.isEnvironment() && this.strength == 0

    fun Site.isMine() = this.owner == id

    fun Site.overkill() =
            if ((this.isEnvironment() && this.strength > 0)) {
                -distanceToEnemyGrid[this.loc.y][this.loc.x]
            } else {
                this.loc.neighbors()
                        .map {
                            if (it.site().isOtherPlayer()) it.site().strength + it.site().production
                            else if (nextMap.getSite(it).isMine()) it.allNeighborsWithin(2)
                                    .filter { nextMap.getSite(it).isMine() }
                                    .map { -nextMap.getSite(it).strength }
                                    .sum()
                            else 0
                        }.sum()
            }

    fun Site.resource() = if (!this.isMine()) {
        if (this.production == 0) 9999
        else (this.strength / (this.production + stillMax).toDouble()).toInt()
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

    fun Location.cornerNeighbors() = listOf<Location>(
            this.move(Direction.NORTH).move(Direction.WEST),
            this.move(Direction.NORTH).move(Direction.EAST),
            this.move(Direction.SOUTH).move(Direction.WEST),
            this.move(Direction.SOUTH).move(Direction.EAST)
    )

    fun Location.move(direction: Direction) = gameMap.getLocation(this, direction)

    fun Location.site() = gameMap.getSite(this)

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

    fun <T> Iterable<T>.shuffle(): List<T> {
        val copy = this.toMutableList()
        ((copy.size - 1) downTo 1).forEach { i ->
            val j = Random().nextInt(i + 1)
            val temp = copy[i]
            copy[i] = copy[j]
            copy[j] = temp
        }
        return copy.toList()
    }
}
