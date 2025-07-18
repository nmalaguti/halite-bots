def debug_list(l):
	if l is None:
		return "None"
	
	s = ",".join([str(e) for e in list(l)])
	return "[%s]" % s
	
def getOppositeDir(dir):
	if dir == 0:
		return 0
	return (((dir - 1) + 2) % 4) + 1
	
gameMap = None
def setGameMap(thisMap):
	global gameMap
	gameMap = thisMap

def getGameMap():
	return gameMap