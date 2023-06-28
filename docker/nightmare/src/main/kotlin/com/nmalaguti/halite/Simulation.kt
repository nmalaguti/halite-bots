package com.nmalaguti.halite

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
