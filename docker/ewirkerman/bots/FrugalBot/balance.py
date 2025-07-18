from util import *
from hlt2 import Location

def getAggressionFactor(gameMap, site):
	t = gameMap.getTerritory()
	#if 2*len(t.frontier) > len(t.territory):
	#	return 7#random.randint(3,7)
	return 5 #SpreadBot	
	
def getSplitWave(gameMap, waves):
	t = gameMap.getTerritory()
	#if 2*len(t.frontier) > len(t.territory):
	#	return len(waves) - 1

	if t.strength < t.production * 3:
		return 1
	return len(waves)/ 2 #SpreadBot	
	
def getSpreadWave(waves):
	return 2
	
theMap = None
def evaluateMapSite(myMap):
	global theMap
	theMap = myMap
	return evaluateSite


def evaluateSite(site):
	t = theMap.getTerritory()
	e_factor = 1
	if type(site) == Location:
		site = theMap.getSite(site)
	str = site.local_strength
	if str < 1:
		str = 1
	enemies = len(site.enemies)
	if enemies and not site.strength:
		e_factor = 255**(enemies*4 - len(site.friends) )
	#return swing_factor*site.local_production/(str**1.1)
	if t.fronts:
		return e_factor*site.local_production/(str**1.5)
	else:
		return e_factor*site.local_production/(str)#SpreadBot
	
def getNeedLimit():
	return 100 #SpreadBot	
	
def checkerOn():
	return True #SpreadBot	
	
def strength_limit(site):
	return 255#SpreadBot	
