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
import sys

sys.path = sys.path + [os.path.dirname(os.path.realpath(__file__))]

##### Logging Setup
import logging
import heapq

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
	
def evaluateSiteToPeak(site):
	swing_factor = 1
	if type(site) == Location:
		site = gameMap.getSite(site)
	str = site.local_strength
	if str < 1:
		str = 1
	if len(site.enemies) > 0:
		swing_factor = 255
		
	peak_factor = 1
	total_str = sum([t.strength for t in gameMap.getTerritories()])
	if gameMap.getTerritory().strength > total_str/len(gameMap.getTerritories()):
		peaks = [peak for peak_set in gameMap.maxima_sets for peak in peak_set]
		logger.debug("peaks: %s" % (peaks))
		min_to_peak = min([gameMap.getDistance(site.loc, peak) for peak in peaks ])
		logger.debug("min_to_peak of %s: %s" % (site.loc, min_to_peak))
		peak_factor = max(5 - min_to_peak, 0) * .03 + 1
	
	ret = swing_factor * site.local_production/(str) * peak_factor
	logger.debug("%s P:%s S: %s = %s" % (site.loc,site.production, site.strength, ret))
	return ret
	
### when total production is low, should be favor sites with lower strength
### when total production is high, should be favor sites with higher production
	


	
def findFronts(t, mf):
	logger.debug("Attacking fronts")

	t.fronts = [f for f in t.fringe if gameMap.getSite(f).strength == 0]
	if len(t.fronts) == 0:
		t.fronts = t.fringe

	m = Attack(gameMap)
	moves = m.get_moves(t.fronts, mf.get_unused_moves(), turnCounter)
	logger.debug("New Attack Map: " + getMoveMap(moves[0]))
	
	return moves
	
	
	
def addressNeeds(needy_locations, mf, need_limit = 100):
	### This is the Need-based assist pattern
	logger.debug("Finding needs")
	t = gameMap.getTerritory()
	needs_copy = [(-evaluateLocal(loc),loc) for loc in needy_locations]
	heapq.heapify(needs_copy)
	
	needs = []
	try:
		while len(needs_copy) > 0:
			logger.debug("needs_copy: %s" % debug_list(needs_copy))
			loc = heapq.heappop(needs_copy)[1]
			site = gameMap.getSite(loc)
			n = Need(site, gameMap, mf.get_unused_moves())
			needs.append(n)
			moves = n.get_moves()
			if len(moves) < need_limit:
				mf.submit_moves(moves)
			#if False and n.generations == 1:
			#	logger.debug("Completing it this turn, marking it")
			#	site.owner = gameMap.playerTag
			#	site.strength = n.strength - site.strength
			#	mark_neighbors(gameMap, loc, "friends")
			#	fdirs = [ getOppositeDir(d) for move in site.friends for d in move.getDirections()]
			#	for dir in [d for d in CARDINALS if not d in fdirs]:
			#		new_loc = gameMap.getLocation(loc, dir)
			#		t.addFringe(new_loc)
			#		gameMap.setupFringeLoc(new_loc)
			#		heapq.heappush(needs_copy, (evaluateLocal(new_loc),new_loc))
			#		t.addFrontier(loc)
				
	except NeedError:
		pass
	
	logger.debug("Need Map (%s and shorter): %s" % (need_limit, getMoveMap([item for sublist in needs for item in sublist.moves])))

	
gameMap = None
contacted = False
def main():
	clock = time.clock()
	global turnCounter
	global gameMap
	
	turnCounter += 1
	logger.debug("****** PREP TURN %d ******" % turnCounter)
	gameMap = getFrame()
	gameMap.turnCounter = turnCounter
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
	
					
	mf.output_all_moves()
	gameMap.clearTerritories()
	logger.debug("****** END TURN %d (time=%s) ******" % (turnCounter,time.clock()-clock))

#testBot()
		
		
try:
	myID, gameMap = getInit(getString)
	if myID != 1:
		logger.setLevel(logging.ERROR)
except Exception as e:
	raise e
sendInit("DevBot")

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
