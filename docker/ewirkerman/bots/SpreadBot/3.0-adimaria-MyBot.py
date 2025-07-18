from hlteric import *
from networkingeric import *
import logging

logging.basicConfig(filename='out.debug',level=logging.DEBUG)

myID, gameMap = getInit()
sendInit("MyPythonBot")

def printBoard(board):
    for row in board:
        element = ''
        for column in row:
            element = element+'|'+str(column)
        logging.debug(element)

def getBoard():
    board = []
    for y in range(gameMap.height):
        row = []
        for x in range(gameMap.width):
            location = Location(x, y)
            site = gameMap.getSite(location)
            if site.owner != myID:
                if site.owner == 0:
                    row.append(site.production*-1)
                else:
                    siteStr = site.strength
                    if site.strength == 0:
                        siteStr = 0.5
                    row.append(siteStr)
            else:
                row.append(0)
        board.append(row)
    printBoard(board)
    return board

def getSelf(board):
    selfSites = []
    r = 0
    for row in board:
        c = 0
        for element in row:
            if element == 0:
                selfSites.append([r,c])
            c += 1
        r += 1
    return selfSites

def getBorders(self, board):

    borders = []
    maxRows = len(board)-1
    maxCols = len(board[self[0]])-1
    borders.append([self[0], self[1]])
    if self[0] != 0:
        borders.append([self[0]-1, self[1]])
    else:
        borders.append([maxRows, self[1]])
    if self[1] != maxCols:
        borders.append([self[0], self[1]+1])
    else:
        borders.append([self[0], 0])
    if self[0] != maxRows:
        borders.append([self[0]+1, self[1]])
    else:
        borders.append([0, self[1]])
    if self[1] != 0:
        borders.append([self[0], self[1]-1])
    else:
        borders.append([self[0], maxCols])
    return borders

def adjustBorders(self, borders, board):
    borderIndex = -1
    for border in borders:
        borderIndex += 1
        borderStr = board[border[0]][border[1]]
        if borderStr < 0 < borderIndex:
            secondBorders = getBorders(border, board)
            for secondBorder in secondBorders:
                if board[secondBorder[0]][secondBorder[1]] > 0:
                    board[self[0]][self[1]] *= -1
                    break
    return board

def escapeSelf(self, board):
    angle = 0
    row = -1
    for r in board:
        row += 1
        breakTrue = 0
        col = -1
        for c in r:
            col += 1
            if c > 0:
                angle = gameMap.getAngle(Location(x=self[1], y=self[0]), Location(x=col, y=row))
                angle = gameMap.getAngleD(angle)
                breakTrue = 1
                break
        if breakTrue == 1:
            break
    logging.debug('angle: %i' % angle)
    if 45 < angle <= 135:
        return SOUTH
    elif 135 < angle <= 225:
        return WEST
    elif 225 < angle <= 315:
        return NORTH
    else:
        return EAST

while True:
    moves = []
    gameMap = getFrame()
    board = getBoard()

    selfElements = getSelf(board)
    for element in selfElements:
        borders = getBorders(element, board)
        board = adjustBorders(element, borders, board)
        selfLocation = Location(y=element[0], x=element[1])
        selfSite = gameMap.getSite(selfLocation)
        if selfSite.strength < 5:
            moves.append(Move(selfLocation, STILL))
        elif selfSite.strength > 200:
            direction = escapeSelf(element, board)
            moves.append(Move(selfLocation, direction))
        else:
            borderIndex = -1
            targetStr = STILL
            maxStr = 0
            targetProd = STILL
            maxProd = selfSite.production
            pickProd = 0
            targetSelf = STILL
            maxSelf = 5
            for border in borders:
                borderVal = board[border[0]][border[1]]
                borderIndex += 1
                if borderVal > 0:
                    if selfSite.strength < borderVal > maxStr:
                        maxStr = borderVal
                        targetStr = borderIndex
                elif borderVal < 0:
                    borderSite = gameMap.getSite(selfLocation, borderIndex)
                    if borderVal <= maxProd and borderSite.strength <= selfSite.strength:
                        maxProd = borderVal
                        targetProd = borderIndex
                else:
                    borderSite = gameMap.getSite(selfLocation, borderIndex)
                    if maxSelf <= borderSite.strength < selfSite.strength >= 5:
                        maxSelf = borderSite.strength
                        targetSelf = borderIndex

            if targetStr != STILL:
                moves.append(Move(selfLocation, targetStr))
            elif targetProd != STILL:
                moves.append(Move(selfLocation, targetProd))
            else:
                moves.append(Move(selfLocation, targetSelf))

    sendFrame(moves)
