package com.nmalaguti.halite

object KotlinBot {
    @Throws(java.io.IOException::class)
    @JvmStatic fun main(args: Array<String>) {
        logger.useParentHandlers = false

        fileHandler.formatter = formatter
        logger.addHandler(fileHandler)

        val iPackage = Networking.getInit()
        val id = iPackage.myID
        var gameMap = iPackage.map
        var turn = 1

        val points = permutations(gameMap)

        Networking.sendInit("MyBetterBot")

        while (true) {
            logger.info("===== Turn: ${turn++} =====")
            val start = System.currentTimeMillis()
            gameMap = Networking.getFrame()

            val moves = points
                    .map { pair ->
                        val (x, y) = pair
                        makeExpandMove(x, y, gameMap, id)
                    }
                    .filterNotNull()

            if (System.currentTimeMillis() - start > 900) {
                Networking.sendFrame(moves)
                continue
            }

            val movedLocations = moves.map { it.loc }.toMutableSet()

            val borderCells = points.map { it.toLocation() }
                    .filter { it.isBorder(gameMap, id) }

            val moreMoves = borderCells
                    .filterNot { it in movedLocations }
                    .map {
                        it to getBestTarget(gameMap, id, it)
                    }
                    .filter { it.second != null }
                    .groupBy { it.second!!.loc }
                    .filter {
                        val site = it.key.site(gameMap).strength
                        val sum = it.value.sumBy { it.first.site(gameMap).strength }
                        sum > site
                    }
                    .flatMap {
                        it.value.map { Move(it.first, it.second!!.direction) }
                    }

            if (System.currentTimeMillis() - start > 900) {
                Networking.sendFrame(moves + moreMoves)
                continue
            }

            movedLocations.addAll(moreMoves.map { it.loc })

            val moreMoreMoves = borderCells
                    .filterNot { it in movedLocations }
                    .filter { it.site(gameMap).strength > 15 }
                    .map {
                        it to getBorders(gameMap, id, it)
                    }
                    .filterNot { it.second.isEmpty() }
                    .map {
                        val site = it.first.site(gameMap)
                        val borders = it.second
                        borders.sortBy { it.site.value() }
                        val best = borders.first()
                        val numberOfTurns = (best.site.strength - site.strength) / site.production.toDouble()

                        if (numberOfTurns > 5) {
                            it.first to borders.find { site.strength > it.site.strength }
                        } else {
                            it.first to null
                        }
                    }
                    .filter { it.second != null }
                    .map { Move(it.first, it.second!!.direction) }

            if (System.currentTimeMillis() - start > 900) {
                Networking.sendFrame(moves + moreMoves + moreMoreMoves)
                continue
            }

            movedLocations.addAll(moreMoreMoves.map { it.loc })

            val considerCombining = borderCells
                    .filterNot { it in movedLocations }
                    .filter { it.site(gameMap).strength > 15 }
                    .map {
                        it to getFriends(gameMap, id, it).filter { it.loc.isBorder(gameMap, id) }
                    }
                    .filterNot { it.second.isEmpty() }
                    .map { friendMove(gameMap, id, it) }
                    .filterNotNull()

            Networking.sendFrame(moves + moreMoves + moreMoreMoves + considerCombining)
        }
    }
}

fun GameMap.isEndGame(): Boolean {
    val freeCells = permutations(this)
            .map { Location(it.first, it.second) }
            .filter { it.site(this).owner == 0 }
            .size.toDouble()

    return (freeCells / (this.width * this.height).toDouble()) < 0.10
}


fun Site.value(isEndGame: Boolean = false) =
        if (isEndGame) {
            this.strength.toDouble()
        } else {
            this.strength / Math.pow(this.production.toDouble(), 2.0)
        }

fun Location.neighborDirections(gameMap: GameMap) = Direction.CARDINALS.map { it to gameMap.getLocation(this, it) }

fun getFriends(gameMap: GameMap, id: Int, loc: Location) =
        loc.neighborDirections(gameMap)
                .map { Border(it.second, it.second.site(gameMap), it.first) }
                .filter { it.site.owner == id }
                .toMutableList()

fun getBorders(gameMap: GameMap, id: Int, loc: Location) =
        loc.neighborDirections(gameMap)
                .map { Border(it.second, it.second.site(gameMap), it.first) }
                .filter { it.site.owner != id }
                .toMutableList()

fun getBestTarget(gameMap: GameMap, id: Int, loc: Location): Border? {
    val borders = getBorders(gameMap, id, loc)

    if (borders.isNotEmpty()) {
        borders.sortBy { it.site.value() }
        return borders.first()
    }

    return null
}

fun friendMove(gameMap: GameMap, id: Int, pair: Pair<Location, List<Border>>): Move? {
    val site = pair.first.site(gameMap)
    val myBestMove = getBestTarget(gameMap, id, pair.first)
    val friendsBestMoves = pair.second
            .map { it to getBestTarget(gameMap, id, it.loc) }
            .filterNot { it.second == null }

    if (myBestMove != null && friendsBestMoves.isNotEmpty()) {
        val friendsToHelp = friendsBestMoves
                .filter {
                    it.second!!.site.value() < site.value() &&
                            it.first.site.strength + site.strength > it.second!!.site.strength
                }
                .sortedBy { it.second!!.site.value() }
                .map { it.first.direction }

        if (friendsToHelp.isNotEmpty()) {
            val best = friendsToHelp.first()
            return Move(pair.first, best)
        }
    }

    return null
}
