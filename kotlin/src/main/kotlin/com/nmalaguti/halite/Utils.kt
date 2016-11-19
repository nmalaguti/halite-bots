package com.nmalaguti.halite


fun Pair<Int, Int>.toLocation() = Location(this.first, this.second)

fun Location.neighbors(gameMap: GameMap) = Direction.CARDINALS.map { gameMap.getLocation(this, it) }

fun Location.site(gameMap: GameMap) = gameMap.getSite(this)

fun Location.toPair() = this.x to this.y

fun Location.isBorder(gameMap: GameMap, id: Int) =
        this.site(gameMap).owner == id &&
                this.neighbors(gameMap).any { it.site(gameMap).owner != id }

fun borderCells(gameMap: GameMap, id: Int) =
        permutations(gameMap)
                .map { it.toLocation() }
                .filter { it.site(gameMap).owner != id }
                .filter { it.neighbors(gameMap).any { it.site(gameMap).owner == id } }
                .map { it.toPair() }
                .distinct()
                .map { it.toLocation() }

fun neighborsLoop(gameMap: GameMap, locations: List<Location>, depth: Int): List<Location> {
    if (depth == 0) {
        return locations
    }

    return locations.map { neighborsLoop(gameMap, it.neighbors(gameMap), depth - 1) }.flatten().distinct()
}

fun myCells(gameMap: GameMap, id: Int) =
        permutations(gameMap)
                .map { it.toLocation() }
                .filter { it.site(gameMap).owner == id }

val pi4 = Math.PI / 4

fun getDirection(gameMap: GameMap, start: Location, end: Location): Move? {
    val angle = gameMap.getAngle(start, end)
    return if (angle >= -pi4 && angle <= pi4) {
//        logger.info("${start}, ${end}, WEST")
        Move(start, Direction.WEST)
    } else if (angle >= pi4 && angle <= 3 * pi4) {
//        logger.info("${start}, ${end}, SOUTH")
        Move(start, Direction.SOUTH)
    } else if (angle >= 3 * pi4 || angle <= 3 * -pi4) {
//        logger.info("${start}, ${end}, EAST")
        Move(start, Direction.EAST)
    } else if (angle >= 3 * -pi4 && angle <= -pi4) {
//        logger.info("${start}, ${end}, NORTH")
        Move(start, Direction.NORTH)
    } else {
        null
    }
}

fun permutations(first: IntRange, second: IntRange) =
        first.flatMap { y ->
            second.map { x ->
                x to y
            }
        }

fun permutations(gameMap: GameMap) = permutations(0 until gameMap.height, 0 until gameMap.width)

fun makeExpandMove(x: Int, y: Int, gameMap: GameMap, id: Int): Move? {
    val loc = Location(x, y)
    val site = gameMap.getSite(loc)
    if (site.owner == id && site.strength > 0) {
        val borders = mutableListOf<Border>()
        for (direction in Direction.CARDINALS) {
            val newLoc = gameMap.getLocation(loc, direction)
            val newSite = gameMap.getSite(newLoc)

            if (newSite.owner != id) {
                borders.add(Border(newLoc, newSite, direction))
            }
        }

        if (borders.size > 0) {
            borders.sortBy { it.site.value(gameMap.isEndGame()) }
            val best = borders.first()

            if (site.strength > best.site.strength) {
                return Move(loc, best.direction)
            }
        } else if (site.strength > 15) {
            val best = neighborsLoop(gameMap, listOf(loc), 4)
                    .filter { it.site(gameMap).owner != id }
                    .sortedBy { it.site(gameMap).value() }
                    .firstOrNull()
            if (best == null) {
                val bestDir = straightClosestEdge(gameMap, id, loc)
                return Move(loc, bestDir)
            } else {
                return getDirection(gameMap, loc, best)
            }
        }
    }
    return null
}

fun straightClosestEdge(gameMap: GameMap, id: Int, loc: Location): Direction {
    var selected = Direction.NORTH
    var selectedDistance = gameMap.width * gameMap.height

    for (direction in Direction.CARDINALS) {
        var distance = 0
        var newLoc = loc
        var newSite: Site
        do {
            distance++
            newLoc = gameMap.getLocation(newLoc, direction)
            newSite = gameMap.getSite(newLoc)
        } while (newSite.owner == id &&
                distance < Math.min(gameMap.width, gameMap.height) / 2 &&
                distance < selectedDistance)

        if (distance < selectedDistance) {
            selectedDistance = distance
            selected = direction
        }
    }

    return selected
}
