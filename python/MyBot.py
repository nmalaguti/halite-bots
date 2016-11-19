from hlt import *
from networking import *
from timeit import default_timer as timer

id, gameMap = getInit()
sendInit("nmalaguti")

maxDepth = min(gameMap.width, gameMap.height) / 2

def straightClosestEdge(gameMap, id, loc):
    results = []
    for direction in CARDINALS:
        distance = 1
        newLoc = loc
        newSite = gameMap.getSite(newLoc)
        while newSite.owner == id and distance < maxDepth:
            distance += 1
            newLoc = gameMap.getLocation(newLoc, direction)
            newSite = gameMap.getSite(newLoc)
        results.append((distance, direction))

    return sorted(results)[0][1]

while True:
    start = timer()
    moves = []
    gameMap = getFrame()
    for y in range(gameMap.height):
        for x in range(gameMap.width):
            if (timer() - start) > 0.3:
                maxDepth -= 1
                continue

            loc = Location(x, y)
            site = gameMap.getSite(loc)

            if site.owner == id and site.strength > 15:
                borders = []
                for direction in CARDINALS:
                    newLoc = gameMap.getLocation(loc, direction)
                    newSite = gameMap.getSite(newLoc)

                    if newSite.owner != id:
                        borders.append((newLoc, newSite, direction))

                if len(borders) > 0:
                    borderTarget = sorted(borders, key=lambda b: (b[1].production, -b[1].strength))[-1]
                    if site.strength > borderTarget[1].strength:
                        moves.append(Move(loc, borderTarget[2]))
                else:
                    direction = straightClosestEdge(gameMap, id, loc)
                    moves.append(Move(loc, direction))

    sendFrame(moves)
