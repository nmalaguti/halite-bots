import sys
import os
sys.path = sys.path + [os.path.dirname(os.path.realpath(__file__))]

from hlt2 import *
from networking2 import *
from moves import *
import logging
import time
from math import pi
from need import *
from attack import Attack
import cProfile
import pstats
import os

##### Logging Setup
import logging
logger = logging.getLogger('bot')

base_formatter = logging.Formatter("%(asctime)s : %(levelname)s %(message)s")
log_file_name = 'bot.debug'
hdlr = logging.FileHandler(log_file_name)
hdlr.setFormatter(base_formatter)
hdlr.setLevel(logging.DEBUG)
logger.addHandler(hdlr)
logger.setLevel(logging.DEBUG)	


test_inputs = [
"1",
"3 3",
"1 2 3 4 5 6 7 8 9"
]

logger.info("Initialized logging")

def remove_sublist(main, sub):
	for m in sub:
		try:
			main.remove(m)
		except ValueError:
			pass
			
def evaluateSite(site):
	swing_factor = 1
	if type(site) == Location:
		site = gameMap.getSite(site)
	str = site.strength
	if str < 1:
		str = 1
	if len(site.enemies) > 0:
		swing_factor = 255
	return swing_factor*site.production/(str*str)
	
def evaluateLocal(site):
	swing_factor = 1
	if type(site) == Location:
		site = gameMap.getSite(site)
	str = site.local_strength
	if str < 1:
		str = 1
	if len(site.enemies) > 0:
		swing_factor = 255
	return swing_factor*site.local_production/(str)
	
### when total production is low, should be favor sites with lower strenght
### when total production is high, should be favor sites with higher production
	


	
def findFronts(t, mf):
	logger.debug("Attacking fronts")

	t.fronts = [f for f in t.fringe if gameMap.getSite(f).strength == 0]
	if len(t.fronts) == 0:
		t.fronts = t.fringe
	
	n = Attack(gameMap)
	return n.get_moves(t.fronts, [], mf.get_unused_moves(), turnCounter)
	
	
	
def addressNeeds(needy_locations, mf, need_limit = 100):
	### This is the Need-based assist pattern
	logger.debug("Finding needs")
	
	needs = []
	try:
		for loc in needy_locations[:]:
			site = gameMap.getSite(loc)
			n = Need(site, gameMap, mf.get_unused_moves())
			needs.append(n)
			moves = n.get_moves()
			if len(moves) < need_limit:
				mf.submit_moves(moves)
	except NeedError:
		pass
	
	#logger.debug("Need Map (%s and shorter): %s" % (need_limit, getMoveMap([item for sublist in needs for item in sublist.moves])))

	
gameMap = None
contacted = False
attackCenters = None
def main():
	clock = time.clock()
	global turnCounter
	global gameMap
	global attackCenters
	
	turnCounter += 1
	logger.debug("****** PREP TURN %d ******" % turnCounter)
	gameMap = getFrame()
	gameMap.turnCounter = turnCounter
	gameMap.lastAttackCenters = attackCenters
	logger.debug("****** START TURN %d ******" % turnCounter)
	
	t = gameMap.getTerritory(myID)
	center = t.getCenter()
	#logger.debug(gameMap.mapToStr(t.getCenter()))
	#logger.debug("My center: \t%s" % str(t.getCenter().getRealCenter()))
	#logger.debug("My frontier: ")
	#for f in t.frontier:
	#	logger.debug(f)
	#logger.debug("My fringe: ")
	#for f in t.fringe:
	#	logger.debug(f)
	
	#logger.debug("Initial moves: %s" % t.unmoved)
	mf = MoveFork(gameMap, t.territory)
	
	needy_locations = sorted(t.fringe, key=evaluateSite)
	needy_locations.reverse()
	#logger.debug("Needies: %s" % debug_list(needy_locations))
	
	needy_locations = sorted(t.fringe, key=evaluateLocal)
	needy_locations.reverse()
	#logger.debug("New Needies: %s" % debug_list(needy_locations))
	
	early_moves, late_moves = findFronts(t, mf)
	#logger.debug("Early Attack Map: " + getMoveMap(early_moves))
	mf.submit_moves(early_moves)
	
	addressNeeds(needy_locations, mf)
	
#	logger.debug("Late Attack Map: " + getMoveMap(late_moves))
	mf.submit_moves(late_moves, weak=True)
	
	#logger.debug("Need map: " + getMoveMap(mf.move_list))
	#logger.debug("Remaining moves: %s" % debug_list(mf.get_unused_moves()))
	
	attackCenters = gameMap.attackCenters 

					
	mf.output_all_moves()
	gameMap.clearTerritories()
	logger.debug("****** END TURN %d (time=%s) ******" % (turnCounter,time.clock()-clock))

#testBot()
		
		
try:
	myID, gameMap = getInit(getString)
except Exception as e:
	with open("bot.debug", "a") as f:
		f.write(e)
sendInit("SpreadBot")

turnCounter = -1

def main_loop():
	while True:
		main()
		#currpath = 'stats\mybot-currturn.stats'
		#lastpath = 'stats\mybot-lastturn.stats'
		#if os.path.exists(currpath):
		#	if os.path.exists(lastpath):
		#		os.remove(lastpath)
		#	os.rename(currpath, lastpath)
		#cProfile.run('main()', currpath)
#cProfile.run('main_loop()', 'stats\mybot.stats')
main_loop()