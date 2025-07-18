from hlteric import *
from networkingeric import *
import logging

logging.basicConfig(filename='Blindbot1.0.debug',level=logging.DEBUG)

myID, gameMap = getInit()
sendInit("BlindwalkerBot_1.0")

def identifyNeighbors(location):
    neighbors = {}
    for direction in CARDINALS:
        neighbour_site = gameMap.getSite(location, direction)
        if(neighbour_site.owner != myID):
            neighbors[direction] = "E"
        else:
            neighbors[direction] = "A"
    return neighbors

def direction(site,location):
    if(site.strength == 0):
        return Move(location, STILL)
    neighbors = identifyNeighbors(location)
    numAllies = 0
    numEnemies = 0
    enemies = {}
    for direction in neighbors:
        if neighbors[direction] is "A":
            numAllies+=1
        else:
            enemies[direction] = gameMap.getSite(location,direction).strength
            numEnemies+=1

    if(numAllies == 4):
        return Move(location,random.choice(DIRECTIONS))
    else:
        for direction in enemies:
            if(site.strength > enemies[direction]):
                return Move(location, direction)
    return Move(location, STILL)

def move(location):
    site = gameMap.getSite(location)
    target_direction = direction(site,location)
    return target_direction

while True:
    moves = []
    gameMap = getFrame()
    for y in range(gameMap.height):
        for x in range(gameMap.width):
            location = Location(x, y)
            if gameMap.getSite(location).owner == myID:
                moves.append(move(location))
    sendFrame(moves)
