package com.nmalaguti.halite

import java.util.*
import kotlin.comparisons.compareBy

val BOT_NAME = "MyCanIDoBetterBotv7"
val MAXIMUM_TIME = 940 // ms
val MAXIMUM_INIT_TIME = 7000 // ms
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
    var startInit = System.currentTimeMillis()
    var lastTurnMoves: Map<Location, Move> = mapOf()
    var playerStats: Map<Int, Stats> = mapOf()
    lateinit var distanceToEnemyGrid: Grid
    lateinit var cellsToEnemyGrid: Grid
    lateinit var cellsToBorderGrid: Grid
    var directedGrid = mapOf<Location, Pair<Int, Int>>()
    var stillMax: Int = 0
    var stillMaxCells = setOf<Location>()
    var madeContact: Boolean = false
    var numPlayers: Int = 0
    var numConnectedPlayers: Int = 0
    var hotSpots = setOf<Location>()
    lateinit var hotSpotsGrid: Grid
    var useHotSpots = true
    var localProduction: Double = 0.0
    lateinit var strengthNeededGrid: Grid
    var minimumStrength = 0
    lateinit var enemyDamageTargets: MutableMap<Location, MutableSet<Movement>>
    lateinit var enemyDamageStrength: MutableMap<Movement, Int>
    var idleStrength: Int = 0

    data class Movement(val origin: Location, val destination: Location)

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

            makeMoves()

            logger.info("stillMax: $stillMax")
            logger.info("idleStrength: $idleStrength")

            endGameLoop()
        }
    }

    fun init() {
        val init = Networking.getInit()
        gameMap = init.gameMap
        id = init.myID

        logger.info("id: $id")

        initHotSpots()

        Networking.sendInit(BOT_NAME)
    }

    fun initHotSpots() {
        hotSpots = findHotSpots()
        hotSpotsGrid = Grid("hotSpotsGrid"){
            if (it in hotSpots) 0
            else 9999
        }

        useHotSpots = hotSpots.isNotEmpty()

        if (useHotSpots) {
            walkHotSpots(hotSpots.toMutableSet())
            logger.info(hotSpotsGrid.toString())
        }

        val myLocation = gameMap.find { it.site().isMine() }
        if (useHotSpots && myLocation != null) {
            var distance = 0
            var count = hotSpots.size

            while (count > 0 && distance < 4) {
                distance++
                count -= (4 * distance)
            }

            localProduction = myLocation.allNeighborsWithin(distance).map { it.site().production }.average()
            val hotSpotProduction = hotSpots.map { it.site().production }.average()

            logger.info("distance: $distance")
            logger.info("localProduction: $localProduction")
            logger.info("hotSpotProduction: $hotSpotProduction")

            if (hotSpotProduction < localProduction) useHotSpots = false
        }

        logger.info("using hot spots: $useHotSpots")
    }

    fun findHotSpots(): Set<Location> {
        // build list of siding window images

        val allHotSpots = mutableSetOf<Location>()
        val grid = gameMap.contents.plus(gameMap.contents)

        (Math.min(15, Math.min(gameMap.width, gameMap.height)) downTo 5).forEach { windowSize ->
            (2..5).forEach { minimumValue ->
                if (System.currentTimeMillis() - startInit > MAXIMUM_INIT_TIME) return allHotSpots

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
                    allHotSpots.addAll(hotSpots)
                }
            }
        }

        return allHotSpots
    }

    fun walkHotSpots(openSet: MutableSet<Location>) {
        val closedSet = mutableSetOf<Location>()
        while (openSet.isNotEmpty()) {
            if (System.currentTimeMillis() - startInit > MAXIMUM_INIT_TIME) {
                useHotSpots = false
                return
            }

            val current = openSet.minBy { it.neighborsAndSelf().map { hotSpotsGrid[it] }.min()!! }!!
            openSet.remove(current)
            if (current !in closedSet) {
                closedSet.add(current)

                if (current.site().isEnvironment() && current.site().strength > 0) {
                    hotSpotsGrid[current] = Math.min(
                            hotSpotsGrid[current],
                            current.site().resource() +
                                    current.neighbors().map { hotSpotsGrid[it] }.min()!!)
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

    // MOVE LOGIC

    fun connectedPlayers(): Set<Int> {
        val openSet = gameMap
                .filter { it.site().isMine() && it.neighbors().any { it.site().isCombat() } }
                .toMutableSet()

        val players = mutableSetOf<Int>()
        bfs(openSet, { current ->
            if (!current.site().isEnvironment()) players.add(current.site().owner)

            if (current.site().isCombat() || current.site().isMine()) {
                current.neighbors()
                        .filterNot { it.site().isEnvironment() && it.site().strength > 0 }
            } else emptyList<Location>()
        })

        return players
    }

    fun buildDistanceToEnemyGrid() {
        cellsToEnemyGrid = Grid("cellsToEnemyGrid") { 9999 }
        walkCellsToEnemyGrid(gameMap.filter { it.site().isOtherPlayer() }.toMutableSet())
        logger.info(cellsToEnemyGrid.toString())

        cellsToBorderGrid = Grid("cellsToBorderGrid") { 9999 }
        walkCellsToBorderGrid(gameMap.filter { it.isInnerBorder() }.toMutableSet())
        logger.info(cellsToBorderGrid.toString())

        val replaceWithHotSpots = !madeContact &&
                useHotSpots &&
                gameMap.filter { it.isOuterBorder() }.map { hotSpotsGrid[it] }.min() ?: 0 < (1000 / localProduction)

        if (replaceWithHotSpots) {
            distanceToEnemyGrid = Grid("distanceToEnemyGrid") { hotSpotsGrid[it] }
        } else {
            distanceToEnemyGrid = Grid("distanceToEnemyGrid") { it.site().resource() }

            directedGrid = gameMap
                    .filter { it.isOuterBorder() && it.site().isEnvironment() && it.site().strength > 0 }
                    .map { it to directedWalk(it) }
                    .toMap()

            directedGrid.forEach {
                val (loc, value) = it

                if (value.second <= distanceToEnemyGrid[loc]) {
                    distanceToEnemyGrid[loc] = value.second
                }
            }

            if (!madeContact) {
                val outerBorder = gameMap
                        .filter { it.isOuterBorder() }
                        .filter { distanceToEnemyGrid[it] < MAXIMUM_STRENGTH }

                val outerBorderValues = outerBorder.map { distanceToEnemyGrid[it] }

                val min = outerBorderValues.min()
                val max = outerBorderValues.max()

                if (min != null && max != null) {
                    val range = max - min
                    val result = range / Math.min(20.0, range.toDouble())

                    logger.info("max: $max, min: $min, range: $range, result: $result")

                    if (range > 0) {
                        outerBorder.forEach {
                            if (distanceToEnemyGrid[it] == min) distanceToEnemyGrid[it] = 0
                            else distanceToEnemyGrid[it] = ((distanceToEnemyGrid[it] - min) / result).toInt() + 1
                        }
                    }
                }
            }
        }

        // final step

        walkDistanceToEnemyGrid(gameMap.filter { it.isOuterBorder() }.toMutableSet())

        logger.info(distanceToEnemyGrid.toString())

        minimumStrength = calculateMinimumStrength()
        logger.info("minimum strength: $minimumStrength")

        // build strength needed grid
        strengthNeededGrid = Grid("strengthNeededGrid") {
            if (it.site().isMine()) {
                Math.max(it.site().production * 5, minimumStrength)
            } else if (it.isOuterBorder()) {
                it.site().strength
            } else 9999
        }

        if (!madeContact) walkStrengthNeededGrid(gameMap.filter { it.isOuterBorder() }.toMutableSet())

        logger.info(strengthNeededGrid.toString())

        // enemy damage potential grid

        enemyDamageTargets = mutableMapOf()
        enemyDamageStrength = mutableMapOf()

        gameMap
                .filter { it.site().isCombat() }
                .flatMap { it.allNeighborsWithin(2).filter { it.site().isOtherPlayer() } }
                .distinct()
                .forEach { origin ->
                    origin.neighborsAndSelf()
                            .filterNot { it.site().isEnvironment() && it.site().strength > 0 }
                            .forEach { destination ->
                                val movement = Movement(origin, destination)
                                enemyDamageStrength[movement] = origin.site().strength
                                destination.neighborsAndSelf()
                                        .forEach { damagedCell ->
                                            enemyDamageTargets.getOrPut(damagedCell, { mutableSetOf() }).add(movement)
                                        }
                            }

                }
    }

    fun calculateMinimumStrength(): Int {
        val bestTargetStrength = gameMap
                .filter { it.isOuterBorder() }
                .filter { it.site().isEnvironment() && it.site().strength > 0 }
                .sortedWith(compareBy({ distanceToEnemyGrid[it] }, { it.site().strength }))
                .firstOrNull()
                ?.site()
                ?.strength ?: 255

        val myProduction = playerStats[id]?.production ?: 1

        return (Math.min(10, myProduction / bestTargetStrength) * MINIMUM_STRENGTH / 5) + stillMax
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

            if (dist > Math.min(gameMap.width, gameMap.height) / 4) continue

            val currAvg = locToValue[currLoc] ?: minAvg

            if (currAvg.second < minAvg.second) minAvg = currAvg

            currLoc.neighbors()
                    .filter { it.site().isEnvironment() && it.site().strength > 0 }
                    .forEach {
                        val nextValue = currAvg.second - ((currAvg.second - it.site().resource().toDouble()) / ((dist + if (madeContact) 4 else 3)))
                        val currValue = locToValue.getOrPut(it, { dist to nextValue }).second
                        if (nextValue < currValue) locToValue[it] = dist to nextValue
                        queue.addLast(it)
                    }
        }

        return minAvg.first.toInt() to minAvg.second.toInt()
    }

    fun walkDistanceToEnemyGrid(openSet: MutableSet<Location>) {
        dijkstras(
                openSet,
                {
                    if (it.site().isMine())
                        it.neighbors().map { distanceToEnemyGrid[it] }.min() ?: 9999
                    else distanceToEnemyGrid[it]
                },
                { current ->
                    if (current.site().isMine()) {
                        distanceToEnemyGrid[current] =
                                Math.min(
                                        distanceToEnemyGrid[current],
                                        1 +
                                                current.neighbors().map { distanceToEnemyGrid[it] }.min()!! +
                                                if (madeContact && cellsToEnemyGrid[current] > 3)
                                                    (Math.max(0.0, Math.log(current.site().production.toDouble() / Math.log(2.0))).toInt())
                                                else if (!madeContact && numPlayers == 2) cellsToBorderGrid[current] / 2
                                                else 0
                                )
                    }

                    current.neighbors().filterNot { it.site().isEnvironment() && it.site().strength > 0 }
                })
    }

    fun walkCellsToEnemyGrid(openSet: MutableSet<Location>) {
        bfs(openSet, { current ->
            if (current.site().isOtherPlayer()) {
                cellsToEnemyGrid[current] = 0
            } else {
                cellsToEnemyGrid[current] = Math.min(
                        cellsToEnemyGrid[current],
                        1 + current.neighbors().map { cellsToEnemyGrid[it] }.min()!!)
            }

            current.neighbors().filterNot { it.site().isEnvironment() && it.site().strength > 0 }
        })
    }

    fun walkCellsToBorderGrid(openSet: MutableSet<Location>) {
        bfs(openSet, { current ->
            if (current.isInnerBorder()) {
                cellsToBorderGrid[current] = 0
            } else {
                cellsToBorderGrid[current] = Math.min(
                        cellsToBorderGrid[current],
                        1 + current.neighbors().map { cellsToBorderGrid[it] }.min()!!)
            }

            current.neighbors().filter { it.site().isMine() }
        })
    }

    fun walkStrengthNeededGrid(openSet: MutableSet<Location>) {
        val targetMap = mutableMapOf<Location, Location>()
        val productionMap = mutableMapOf<Location, Pair<Int, MutableSet<Location>>>()

        dijkstras(
                openSet,
                { distanceToEnemyGrid[it] },
                { current ->
                    if (current.isOuterBorder()) {
                        strengthNeededGrid[current] = current.site().strength
                        targetMap[current] = current
                        productionMap[current] = 0 to mutableSetOf()
                    } else if (current.site().isMine()) {
                        val next = current.neighbors()
                                .filter {
                                    distanceToEnemyGrid[it] < distanceToEnemyGrid[current] &&
                                            it in targetMap
                                }
                                .minBy { distanceToEnemyGrid[it] }

                        if (next != null) {
                            val target = targetMap[next]
                            val (distVisited, productionLocations) = productionMap[target] ?: 0 to mutableSetOf()
                            if (target != null) {
                                val distance = gameMap.getDistance(current, target).toInt()
                                if (distance > distVisited) {
                                    strengthNeededGrid[target] = Math.max(0, strengthNeededGrid[target] - productionLocations.sumBy { it.site().production })
                                    productionMap[target] = distance to productionLocations
                                }

                                targetMap[current] = target
                                if (strengthNeededGrid[target] > 0) {
                                    strengthNeededGrid[current] = strengthNeededGrid[target]
                                    strengthNeededGrid[target] = Math.max(0, strengthNeededGrid[target] - current.site().strength)

                                    productionLocations.add(current)
                                }
                            }
                        }
                    }

                    current.neighbors()
                            .filter { it.site().isMine() }
                            .sortedBy { it.site().strength }
                })
    }

    fun makeMoves() {
        val battleBlackout = mutableSetOf<Location>()
        val blackoutCells = mutableSetOf<Location>()

        val sources = mutableMapOf<Location, Direction>()
        val destinations = mutableSetOf<Location>()

        fun Location.swappable(source: Location) =
                this != source &&
                        this.site().isMine() &&
                        source.site().isMine() &&
                        this !in sources && source !in sources &&
                        this !in destinations && source !in destinations &&
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

        fun undoMove(source: Location, target: Location) {
            if (source !in sources || sources[source] == Direction.STILL) {
                logger.warning("Asked to undo a move from $source but did not move that location.")
                return
            }

            val originSite = gameMap.getSite(source)
            val startSite = nextMap.getSite(source)
            val destinationSite = nextMap.getSite(target)

            if (startSite.owner == destinationSite.owner) {
                startSite.strength += originSite.strength
                destinationSite.strength -= originSite.strength
            } else {
                logger.warning("Asked to undo an attack move from $source but that's too hard.")
            }
        }

        fun finalizeMove(source: Location, target: Location, addToBattleBlackout: Boolean, preventSwaps: Boolean) {
            if (target.swappable(source) &&
                    nextMap.getSite(target).strength + source.site().strength >= MAXIMUM_STRENGTH) {

                enemyDamageTargets[source]?.forEach {
                    enemyDamageStrength[it] = enemyDamageStrength[it]!! - target.site().strength
                    enemyDamageStrength[it] = Math.max(0, enemyDamageStrength[it]!!)
                }

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
                enemyDamageTargets[target]?.forEach {
                    enemyDamageStrength[it] = enemyDamageStrength[it]!! - source.site().strength
                    enemyDamageStrength[it] = Math.max(0, enemyDamageStrength[it]!!)
                }

                val move = moveTowards(source, target)

                if (target in battleBlackout || (target in blackoutCells && source.site().strength != 255)) logger.warning("target $target in blackout cells")

                if (preventSwaps) {
                    val moveFromDestination = lastTurnMoves[move.loc.move(move.dir)]
                    if (moveFromDestination != null && moveFromDestination.loc.move(moveFromDestination.dir) == move.loc) {
                        return
                    }
                }

                if (source in sources) undoMove(source, source.move(sources[source]!!))

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

        /////////////////////////////////////////////////////

        gameMap
                .filter { it.site().isMine() && it.site().strength > 0 }
                .filter { it.neighbors().any { it.site().isCombat() } }
                .filterNot { it.site().strength < it.site().production * 2 || it.site().strength < MINIMUM_STRENGTH }
                .sortedWith(compareBy({ -it.site().strength }, { cellsToEnemyGrid[it] }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    if (loc in sources) return@forEach

                    if (loc.allNeighborsWithin(3).none { it.site().isOtherPlayer() }) {
                        loc.neighbors()
                                .filter { it.site().isCombat() }
                                .filter {
                                    loc.site().strength > Math.max(loc.site().production * 2, MINIMUM_STRENGTH) &&
                                            it.nextSite().strength + loc.site().strength < MAXIMUM_STRENGTH
                                }
                                .sortedWith(compareBy(
                                        { -it.site().production },
                                        { cellsToEnemyGrid[it] }
                                ))
                                .firstOrNull()
                                ?.let { target ->
                                    finalizeMove(loc, target, false, false)
                                }

                    } else {
                        val target = loc.neighbors()
                                .filter { it !in battleBlackout }
                                .filterNot { it.site().isEnvironment() && it.site().strength > 0 }
                                .filter {
                                    enemyDamageTargets[it]?.groupBy { it.origin }?.all {
                                        it.value.any {
                                            it.origin.site().strength == enemyDamageStrength[it] ||
                                                    enemyDamageStrength[it]!! > loc.site().strength
                                        }
                                    } ?: true
                                }
                                .filter {
                                    if (it.site().isEnvironment() && it.site().strength > 0)
                                        numPlayers == 2 && enemyDamageTargets[it]?.all { origin ->
                                            val enemyStrength = playerStats[origin.origin.site().owner]?.strength ?: 0
                                            val myStrength = playerStats[id]?.strength ?: 0 - it.site().strength
                                            myStrength > enemyStrength
                                        } ?: true && loc.site().strength > it.site().strength
                                    else true
                                }
                                .filter {
                                    if (it != loc && it.nextSite().isMine())
                                        it.nextSite().strength + loc.site().strength < MAXIMUM_STRENGTH || it.swappable(loc)
                                    else true
                                }
                                .sortedWith(compareBy(
                                        {
                                            enemyDamageTargets[it]
                                                    ?.groupBy { it.origin }
                                                    ?.map {
                                                        it.value.map {
                                                            -enemyDamageStrength[it]!!
                                                        }.max()!!
                                                    }?.sum() ?: 0
                                        },
//                                        {
//                                            enemyDamageTargets[it]
//                                                    ?.distinctBy { it.origin }
//                                                    ?.map {
//                                                        -it.origin.site().strength
//                                                    }?.sum() ?: 0
//                                        },
                                        { if (it.site().isMine()) it.site().strength else 0 },
                                        { if (it.site().isEnvironment() && it.site().strength > 0) 1 else 0 },
                                        { cellsToEnemyGrid[it] },
                                        { -it.site().production },
                                        { -it.neighbors().filter { nextMap.getSite(it).isOtherPlayer() }.size }
                                ))
                                .firstOrNull()

                        if (target != null) {
                            finalizeMove(loc, target, true, false)
                        } else {
                            enemyDamageTargets[loc]?.forEach {
                                enemyDamageStrength[it] = enemyDamageStrength[it]!! - loc.site().strength
                                enemyDamageStrength[it] = Math.max(0, enemyDamageStrength[it]!!)
                            }
                        }
                    }
                }

        gameMap
                .filter { it.site().isMine() && it !in sources }
                .sortedWith(compareBy({ cellsToEnemyGrid[it] }, { distanceToEnemyGrid[it] }, { -it.site().strength }, { it.neighbors().filterNot { it.site().isMine() }.size }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val target = loc.neighbors()
                            .filter { it !in battleBlackout }
                            .filter { it !in blackoutCells || loc.site().strength == 255 }
                            .filter { distanceToEnemyGrid[it] < distanceToEnemyGrid[loc] }
                            .filter {
                                val nextSite = nextMap.getSite(it)

                                if (nextSite.isCombat()) {
                                    false
                                } else if (nextSite.isEnvironment()) {
                                    // environment
                                    nextSite.strength < loc.site().strength
                                } else {
                                    // mine
                                    loc.site().strength > strengthNeededGrid[loc] &&
                                            (nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH || it.swappable(loc))
                                }
                            }
                            .sortedWith(compareBy(
                                    { distanceToEnemyGrid[it] },
                                    { cellsToEnemyGrid[it] },
                                    { if (it in directedGrid) directedGrid[it]!!.first else 0 },
                                    { if (!madeContact && numPlayers == 2) it.site().strength else 0 },
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
                                                        distanceToEnemyGrid[it],
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
                .sortedWith(compareBy({ distanceToEnemyGrid[it] }, { -it.site().overkill() }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val wouldAttack = loc.neighbors()
                            .filter {
                                it.site().isMine() &&
                                        it !in sources &&
                                        it.site().strength > strengthNeededGrid[it]
                            }
                            .filter { distanceToEnemyGrid[it] > distanceToEnemyGrid[loc] }

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

        gameMap
                .filter { it.site().isMine() && it !in sources && it.site().strength == 255 }
                .sortedWith(compareBy({ cellsToEnemyGrid[it] }, { distanceToEnemyGrid[it] }, { -it.site().strength }, { it.neighbors().filterNot { it.site().isMine() }.size }))
                .forEach { loc ->
                    if (System.currentTimeMillis() - start > MAXIMUM_TIME) return

                    val target = loc.neighbors()
                            .filter { it !in battleBlackout }
                            .filter { it !in blackoutCells || loc.site().strength == 255 }
                            .filter { distanceToEnemyGrid[it] <= distanceToEnemyGrid[loc] }
                            .filter {
                                val nextSite = nextMap.getSite(it)

                                if (nextSite.isEnvironment() && nextSite.strength == 0) {
                                    false
                                } else if (nextSite.isEnvironment()) {
                                    // environment
                                    nextSite.strength < loc.site().strength
                                } else {
                                    // mine
                                    loc.site().strength > strengthNeededGrid[loc] &&
                                            (nextSite.strength + loc.site().strength < MAXIMUM_STRENGTH || it.swappable(loc))
                                }
                            }
                            .sortedWith(compareBy(
                                    { distanceToEnemyGrid[it] },
                                    { cellsToEnemyGrid[it] },
                                    { if (it in directedGrid) directedGrid[it]!!.first else 0 },
                                    { if (!madeContact && numPlayers == 2) it.site().strength else 0 },
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
                                                        distanceToEnemyGrid[it],
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

        idleStrength = gameMap
                .filter { it.site().isMine() && it !in sources && it.site().strength > strengthNeededGrid[it] }
                .map { it.site().strength }
                .sum()

        stillMaxCells = gameMap
                .filter { it.site().isMine() && it !in sources && it.site().strength == 255 }
                .toSet()

        stillMax =
                if (playerStats[id]?.strength == playerStats.map { it.value.strength }.max())
                    idleStrength / 255
                else
                    stillMaxCells.size
    }

    // EXTENSIONS METHODS

    // SITE

    fun Site.isOtherPlayer() = !this.isMine() && !this.isEnvironment()

    fun Site.isEnvironment() = this.owner == 0

    fun Site.isCombat() = this.isEnvironment() && this.strength == 0

    fun Site.isMine() = this.owner == id

    fun Site.overkill() =
            if ((this.isEnvironment() && this.strength > 0)) {
                -distanceToEnemyGrid[this.loc]
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
        if (this.production == 0 || (this.isEnvironment() && this.strength == 255)) 9999
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

    fun Location.nextSite() = nextMap.getSite(this)

    fun Location.allNeighborsWithin(distance: Int) = gameMap.filter { gameMap.getDistance(it, this) <= distance }

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

    fun dijkstras(openSet: MutableSet<Location>, minBy: (Location) -> Int, each: (Location) -> Iterable<Location>) =
            walkSet(openSet, { it.minBy(minBy) }, each)

    fun bfs(openSet: MutableSet<Location>, each: (Location) -> Iterable<Location>) =
            walkSet(openSet, { it.first() }, each)

    fun walkSet(
            openSet: MutableSet<Location>,
            pickNext: (MutableSet<Location>) -> Location?,
            each: (Location) -> Iterable<Location>) {
        val closedSet = mutableSetOf<Location>()
        while (openSet.isNotEmpty()) {
            val current = pickNext(openSet)
            if (current != null) {
                openSet.remove(current)
                if (current !in closedSet) {
                    closedSet.add(current)

                    openSet.addAll(each(current))
                }
            }
        }
    }

    class Grid(val name: String, initializer: (Location) -> Int = { 0 }) {
        val grid: MutableList<MutableList<Int>>

        init {
            grid =
                    (0 until gameMap.height)
                            .map { y ->
                                (0 until gameMap.width)
                                        .map { x ->
                                            initializer(Location(x, y))
                                        }
                                        .toMutableList()
                            }
                            .toMutableList()
        }

        operator fun get(loc: Location) = grid[loc.y][loc.x]

        operator fun set(loc: Location, value: Int) {
            grid[loc.y][loc.x] = value
        }

        override fun toString(): String {
            val builder = StringBuilder("\n$name\n")
            builder.append("     ")
            builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
            builder.append("\n")
            for (y in 0 until gameMap.height) {
                builder.append("$y".take(3).padEnd(4) + " ")
                builder.append(grid[y].mapIndexed { x, it ->
                    val value: Any? = if (Location(x, y).site().isMine()) it.toDouble() else it
                    "$value".take(3).padEnd(4) }.joinToString(" ")
                )
                builder.append(" " + "$y".take(3).padEnd(4))
                builder.append("\n")
            }
            builder.append("     ")
            builder.append((0 until gameMap.width).map { "$it".take(3).padEnd(4) }.joinToString(" "))
            builder.append("\n")

            return builder.toString()
        }
    }
}
