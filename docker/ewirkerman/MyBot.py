import gc
import os
import sys

sys.path = sys.path + [os.path.dirname(os.path.realpath(__file__))]

import cProfile

##### Logging Setup
import logging
import os
import pstats
import threading
import time
from collections import deque
from inspect import currentframe, getframeinfo

#import objgraph
from math import pi
from timeit import default_timer as timer

import balance
from claim import *
from hlt2 import *
from moves import *
from networking2 import *

logger = logging.getLogger('bot')

base_formatter = logging.Formatter("%(message)s")
log_file_name = 'bot.'+'debug'
hdlr = logging.FileHandler(log_file_name)
hdlr.setFormatter(base_formatter)
hdlr.setLevel(logging.DEBUG)
logger.addHandler(hdlr)
logger.setLevel(logging.DEBUG)	


# with open("bot." + "debug", "a") as f:
	# logger.debug("Growth at %s" % getframeinfo(currentframe()).lineno)
	# objgraph.show_growth(limit=20,file=f)
logger.info("Initialized logging")

def remove_sublist(main, sub):
	for m in sub:
		try:
			main.remove(m)
		except ValueError:
			pass
		
def show_root_map(claims):
	for root in claims:
		move_dict = {}
		for gen in root.gens.values():
			for claim in gen.claims:
				move_dict[claim.loc] = "%s%s" % ("S^>v<"[claim.get_parent_direction()],int(claim.value*1000))
		logger.debug("root map %s %s:\n%s has best trail: %s\n%s" % (root.gameMap().turnCounter, root.loc, root, root.loc.trails[0], root.max_gen and getMoveMap(move_dict = move_dict, gameMap=root.gameMap()) or "None"))
		if root.max_gen:
			pass
	
def claims_to_map(title, turnCounter, claims):
	m_d = {}
	for claim in claims:
		m_d.update(claim.build_map_dict())
	if m_d:
		logger.debug("%s %s: %s" % (title, turnCounter, getMoveMap(move_dict = m_d)) )
		pass

def layered_iteration(seed, gameMap, func, sort_func=None, limit=None, owner=None):
	if owner is None:
		owner = gameMap.playerTag
	curr_wave = list(seed)
	done = set(seed)
	results = []
	time_limit = .99
	limit = 1000
	
	if gameMap.width * gameMap.height * .75 < len(gameMap.getTerritory().territory):
		limit = 20
	
	while curr_wave and limit:
		limit -= 1
		if sort_func:
			curr_wave.sort(key=sort_func)
		# logger.debug("\nIterating over gen: %s\n" % debug_list(curr_wave))
		next_wave = []
		for loc in curr_wave:
			# logger.debug("Iterating over %s with owner %s" % (loc, loc.site().owner) )
			if owner == 0:
				raise Exception("Not yet implemented")
				pass
			elif owner != gameMap.playerTag:
				for move in loc.site().enemies:
					child_loc = move.loc
					if child_loc.site().owner == owner:
						if child_loc not in done:
							done.add(child_loc)
							next_wave.append(child_loc)
							# logger.debug("Iteration child %s" % child_loc)
			else:
				for move in loc.site().friends:
					child_loc = move.loc
					if child_loc not in done:
						done.add(child_loc)
						next_wave.append(child_loc)
						# logger.debug("Iteration child %s" % child_loc)
			
			if func:
				r = func(loc)
				if r is not None:
					results.append(r)
			# pass
			
			# logger.debug("")
			if limit and (timer() - gameMap.clock) > time_limit:
				logger.debug("Aborting turn due to time limit! curr_time = %s" % (timer()-gameMap.clock))
				return results
		curr_wave = next_wave
	return results
	

exploration_depth = 6
def take_turn(gameMap):
	global exploration_depth
	####### Seed the location Trail
	if gameMap.turnCounter < 1:
		for y in range(gameMap.height):
			for x in range(gameMap.width):
				l = gameMap.getLocationXY(x,y)
				t = Trail(new_loc=l)
				l.expanding_trails = [t]
				l.trails = [t]
	

		
	######################################## BOT OPTIONS
	gameMap.multipull = False
	gameMap.chunkedpull = gameMap.multipull and False
	gameMap.breakthrough = True
	gameMap.breakthrough_hold_range = 1
	gameMap.breakthrough_pull_range = 5
	gameMap.breach_separation = 7
	gameMap.trail_search_distance_max = exploration_depth
	gameMap.gen_cap = False
	########################################
		
	
	logger.debug("****** START TURN %d ******" % turnCounter)
	
	t = gameMap.getTerritory()
	mf = MoveFork(gameMap, t.territory)
	

	######## Root Claim Generation
	
	all_capped_claims = []
	all_uncapped_claims = []
	all_capped_claims = []
	for loc in t.fringe:
		# if not any([n.enemies for n in gameMap.neighbors(loc, 1, True)]):
			# c_claim = CappedClaim(gameMap, loc)
			# all_capped_claims.append(c_claim)
		# el
		if all([gameMap.get_friendly_strength(move.loc, dist = 3, type="enemies") < move.loc.site().strength for  move in loc.site().neutrals]):
			c_claim = CappedClaim(gameMap, loc)
			all_capped_claims.append(c_claim)
	
	
	#### Balancing of uncapped values
	percentile_capped_list = list(all_capped_claims)
	heapq.heapify(percentile_capped_list)
	keep_top_percent = .2
	for	i in range(int(len(all_capped_claims)*keep_top_percent)):
		heapq.heappop(percentile_capped_list)
	gameMap.target_uncapped_value = percentile_capped_list and percentile_capped_list[0].value or 1
	# logger.debug("target_uncapped_value = %s" % gameMap.target_uncapped_value)
	
	enemy_roots = set()
	for terr in gameMap.getEnemyTerritories():
		for loc in terr.fringe:
			if loc.site().strength == 0 or loc.site().friends:
				enemy_roots.add(loc)
	
	for loc in enemy_roots:
		claim = UncappedClaim(gameMap, loc)
		logger.debug("Created enemy_fringe claim: %s" % claim)
		all_uncapped_claims.append(claim)
	
	all_capped_claims = [claim for claim in all_capped_claims if claim.value > 0]
	
	logger.debug("****** CLAIM DONE %d\t(time=%s) ******" % (turnCounter,timer()-gameMap.clock))
	
	####### Trail depth expansion
	for claim in all_capped_claims:
		logger.debug("Peering into %s's expanding_trails: %s" % (claim.loc,claim.loc.expanding_trails))
		while claim.loc.can_explore():
			start_peer = timer()
			claim.loc.peer_deeper()
			peer_time = timer() - start_peer
		explored.add(claim.loc)
				
		# logger.debug("Made trail of length %s" % len(claim.loc.get_best_trail()))
		claim.trail = claim.loc.get_best_trail()
		
		# prioritizing the exploration of these paths because they're going to come up next
		next_batch = [loc for loc in claim.trail.path if loc not in claim.trail and loc not in explored]
		next_batch.reverse()
		explore_queue.extendleft(next_batch)
		# logger.debug("Best trail for %s is %s" % (claim.loc, claim.trail))
		claim.recalc_value()
	
	# while any([claim.expanding_trails for claim in all_capped_claims]):
		# [claim.peer_deeper() for claim in all_capped_claims if claim.expanding_trails]
	
	# logger.debug("min_time = %s, max_time = %s" % (min_time, max_time))
	
	logger.debug("****** TRAIL DEPTH %d\t(time=%s) ******" % (turnCounter,timer()-gameMap.clock))
	
	####### Natural separation if there are no outside threats
	all_uncapped_claims = [claim for claim in all_uncapped_claims if claim.value > 0]
	if not all_uncapped_claims:
		for claim in all_capped_claims:
			uc_claim = UncappedClaim(gameMap, claim.loc)
			uc_claim.value = .0000001
			all_uncapped_claims.append(uc_claim)
	
	
	
	logger.debug("\nall_capped_claims: \n%s" % "\n".join([claim.__str__() for claim in all_capped_claims]) )
	logger.debug("\nall_uncapped_claims: \n%s" % "\n".join([claim.__str__() for claim in all_uncapped_claims]) )
	
	
	######## Claim Spreading
	
	for l in [list(all_uncapped_claims), list(all_capped_claims)]:
		for claim in l:
			claim.site().heap.add_claim(claim)
		working_list = [c for c in l if c.is_top_claim()]
		while working_list:
			for claim in working_list:
				# logger.debug("")
				# logger.debug("Expanding seed %s claim %s" % (claim.max_gen, claim) )
				claim.spread()
				
			working_list = [c for c in l if c.still_expanding]
		logger.debug("\n\n")
		# for root in l:
			# move_dict = {}
			# if root.max_gen < 1:
				# continue
			# for gen in root.gens.values():
				# for claim in gen.claims:
					# move_dict[claim.loc] = claim.gen
			# logger.info("root map %s: %s" % (turnCounter, getMoveMap(move_dict = move_dict)))
	logger.debug("Spread map %s: %s" % (turnCounter, getMoveMap(layered_iteration(t.fringe, gameMap, get_planned_move), gameMap)))

	logger.debug("****** SPREAD DONE %d\t(time=%s) ******" % (turnCounter,timer()-gameMap.clock))

		
	
	
	
	logger.debug("I really think I want to start undoing claims if they can't finish and there is a different capped claim underneath them")
	
	
	####### Root map generation
	# show_root_map(all_capped_claims) # .debug
	# show_root_map(all_uncapped_claims) # .debug
	
	
	
	
	
	
	######## Claim Move Filtering
		
	logger.debug("")
	logger.debug("Getting capped root claims as moves of %s's territory" % t.owner)
	
	layered_iteration(t.fringe, gameMap, find_loc_move, sort_func = gameMap.num_non_friends, limit=True)
	moves = layered_iteration(t.frontier, gameMap, get_loc_move)
	
	logger.debug("****** FILTER DONE %d\t(time=%s) ******" % (turnCounter,timer()-gameMap.clock))
	
	# # showing inbound strength
	
	# loc_inbound = [check_inbound_damage(loc) for loc in list(t.fringe)+list(t.frontier) if loc.gameMap().get_enemy_strength(loc)]
	
	# logger.info("inb map %s: %s" % (turnCounter, getMoveMap(move_dict = dict(loc_inbound))))
	
	logger.debug("Moves map %s: %s" % (turnCounter, getMoveMap(moves, gameMap)))
	
	
	mf.submit_moves(moves)
	
	if not explore_queue:
		logger.debug("explore_queue was empty - reseeding from fringe")
		explore_queue.extend([loc for loc in gameMap.getTerritory().fringe if not any([n.enemies for n in gameMap.neighbors(loc, 1, True)]) and loc.site().strength])
		
	try:
		result = True
		while timer()-gameMap.clock < .95 and result:
			result = seek()
		logger.debug("Time is up, so rejoining!")
	except IndexError as e:
		logger.debug(e)
		logger.debug("There's nothing in the world left to think about, so I'm going to increase search depth")
		exploration_depth += 1
	
	move_dict = {}
	for loc in explore_queue:
		move_dict[loc] = "="
	for loc in explored:
		move_dict[loc] = "X"
	logger.debug("Explore map %s:%s" % (turnCounter, getMoveMap(move_dict = move_dict, gameMap=gameMap)))
		
	logger.debug("****** EXPLORATION DONE %d\t(time=%s) ******" % (turnCounter,timer()-gameMap.clock))
	
	mf.output_all_moves()
	del gameMap
	del mf
	# if turnCounter >= 2:
		# raise Exception("Test Frame Ended")
	
explore_queue = deque()
gotFrameLock = threading.Lock()
explored = set()

def seek():
	if not explore_queue:
		return False
	peer_target = explore_queue.popleft()
	if peer_target.can_explore():
		logger.debug("Peering at %s" % peer_target)
		peer_target.peer_deeper()
		explore_queue.appendleft(peer_target)
	else:
		# logger.debug("Expanding from %s" % peer_target)
		explored.add(peer_target)
		
		children = set()
		for trail in peer_target.trails:
			children.update([loc for loc in trail.path if loc not in explore_queue and loc not in explored and loc.site().owner == 0])
		explore_queue.extend(children)
	return True
	
def explore():
	logger.debug("Exploring!")

	try:
		while not gotFrameLock.acquire(blocking=False):
			seek()
		logger.debug("Next frame is ready, so rejoining!")
	except IndexError as e:
		logger.debug(e)
		logger.debug("There's nothing in the world left to think about")
		pass
	except Exception as e:	
		logger.debug("Something went wrong: %s" % e )
		pass
	finally:
		gotFrameLock.release()
	
		
	
def main():
	
	global turnCounter
	global last_map
	
	turnCounter += 1
	logger.debug("****** PREP TURN %d ******" % turnCounter)
	test_frame = None
	# test_frame = "625 0 1 1 1249 0 1 2 624 0 136 163 158 131 104 102 118 133 133 127 111 102 100 96 89 87 100 131 150 128 79 43 29 27 28 31 33 39 54 73 82 75 60 50 42 31 26 30 42 51 52 49 43 37 34 34 41 53 68 95 108 130 128 111 97 99 108 113 110 104 91 88 96 103 103 102 112 134 147 126 81 49 39 40 43 43 40 41 52 69 79 74 61 52 44 35 30 33 41 47 47 44 39 36 36 37 40 46 55 76 81 98 100 94 93 101 103 98 91 83 71 73 94 118 127 125 125 131 133 114 79 58 55 62 67 64 51 42 46 58 66 65 59 53 46 40 37 37 39 40 40 38 37 38 42 44 43 40 43 57 64 80 86 88 101 117 118 106 98 87 70 70 93 124 137 135 126 121 115 100 80 70 73 81 86 80 59 41 37 43 50 51 50 47 42 38 37 37 38 38 37 36 38 43 48 49 44 37 36 45 57 75 87 98 121 146 145 126 112 100 80 73 85 106 117 118 113 106 97 86 77 76 80 86 90 84 59 37 31 35 39 40 38 36 33 30 30 32 36 39 38 37 40 43 45 43 38 32 32 40 63 88 106 120 145 167 159 128 107 95 79 70 71 78 85 90 90 84 75 66 63 65 69 72 74 71 53 37 33 37 41 40 36 33 30 28 26 27 32 36 36 36 38 40 39 35 32 32 35 44 71 97 116 127 144 155 140 106 83 73 64 57 55 55 58 65 67 61 52 47 46 48 50 49 50 51 46 43 45 52 58 56 48 43 40 36 30 27 28 30 31 31 33 35 33 31 33 39 45 54 61 73 84 88 95 98 86 64 49 44 39 35 34 34 38 44 47 42 37 35 36 37 36 33 34 42 53 62 68 74 80 77 65 57 53 46 37 30 28 27 26 24 25 26 27 29 36 48 56 57 48 47 48 47 47 47 41 32 26 24 21 19 20 22 26 32 35 33 31 31 31 29 27 25 29 48 75 95 96 91 90 83 69 60 57 51 42 36 32 29 25 21 20 20 22 27 37 53 62 57 43 36 33 29 26 24 22 20 18 16 15 15 16 17 21 26 30 31 31 30 28 25 23 23 31 57 93 116 109 89 77 69 59 55 54 51 47 44 38 33 27 22 19 19 20 26 38 55 64 57 39 32 29 25 21 19 18 16 15 13 14 15 16 17 19 23 28 33 35 34 32 30 28 29 36 56 84 98 88 68 58 54 54 57 59 57 56 55 46 36 28 22 19 18 20 25 35 49 57 51 34 30 28 26 22 21 19 17 14 14 15 17 18 19 21 24 31 40 46 47 47 47 44 42 43 50 60 64 57 48 46 50 56 63 65 62 62 61 49 37 28 22 18 19 23 28 34 42 46 42 33 31 32 32 31 30 28 22 18 17 20 22 23 26 28 32 42 57 69 74 75 74 68 60 54 50 46 41 36 34 37 43 51 58 57 52 50 49 40 32 27 22 19 22 28 32 35 40 41 38 37 38 43 47 50 50 45 35 27 24 26 29 34 43 49 55 67 86 101 105 105 102 92 79 69 57 42 30 25 24 28 36 44 49 45 37 32 30 26 23 22 19 19 22 26 29 33 37 40 39 47 55 64 70 76 76 66 50 37 33 35 42 55 75 86 93 103 116 122 118 113 109 101 89 79 65 44 29 22 20 23 29 36 39 34 26 22 20 19 19 19 19 19 19 20 22 26 33 39 42 60 75 86 91 96 96 84 64 46 39 42 53 76 107 124 130 136 136 127 112 102 98 94 90 88 74 51 33 23 19 20 23 25 25 23 20 21 21 22 23 24 23 21 19 16 17 22 32 41 49 66 80 88 90 93 94 84 65 47 39 40 51 78 111 128 132 135 131 118 103 94 93 92 95 98 85 59 37 24 18 15 15 15 15 16 20 25 29 32 33 31 27 22 18 15 16 23 35 45 53 62 70 72 69 70 70 66 55 45 40 38 45 65 89 97 99 105 111 109 101 96 97 95 99 103 88 60 36 23 15 11 10 10 11 14 20 29 36 41 41 36 27 20 16 14 16 25 37 46 53 57 63 63 58 53 51 52 53 52 51 46 47 58 68 66 65 75 90 98 96 91 90 87 89 90 74 49 29 19 13 9 8 8 9 14 21 29 36 41 39 32 23 17 13 13 15 22 32 40 48 61 69 71 66 56 48 51 61 66 68 60 55 59 58 48 44 51 64 72 70 65 64 64 70 71 56 37 23 17 13 11 10 9 11 15 22 28 33 34 31 25 17 13 11 11 14 19 26 33 45 83 97 100 93 73 57 59 73 81 81 68 59 59 53 42 35 37 46 52 50 44 43 47 54 56 46 32 24 20 19 19 18 16 16 19 25 29 29 27 23 18 14 11 9 11 14 18 23 33 54 116 137 139 126 95 70 70 87 96 93 74 58 54 50 42 34 34 41 46 44 38 35 39 47 49 43 36 32 30 30 31 29 26 23 25 30 31 27 21 16 14 12 10 9 11 15 19 23 37 72 124 150 152 137 102 74 75 94 105 104 82 60 51 47 44 40 40 44 47 43 36 33 39 47 51 48 45 42 40 40 40 38 32 28 29 34 35 27 17 12 11 11 10 9 10 14 18 22 37 75 102 127 132 119 89 68 72 91 104 107 88 65 53 48 48 52 54 54 50 42 34 32 39 49 52 50 47 45 43 41 41 38 32 27 29 35 35 26 15 11 12 12 10 8 8 11 14 17 29 60 75 97 102 92 70 57 65 83 96 101 86 67 57 51 55 65 70 65 54 42 33 32 39 48 50 45 42 39 36 35 35 34 28 23 25 31 31 22 14 12 14 15 12 8 7 8 11 13 21 43 75 97 102 92 70 57 65 83 96 101 86 67 57 51 55 65 70 65 54 42 33 32 39 48 50 45 42 39 36 35 35 34 28 23 25 31 31 22 14 12 14 15 12 8 7 8 11 13 21 43 102 127 132 119 89 68 72 91 104 107 88 65 53 48 48 52 54 54 50 42 34 32 39 49 52 50 47 45 43 41 41 38 32 27 29 35 35 26 15 11 12 12 10 8 8 11 14 17 29 60 124 150 152 137 102 74 75 94 105 104 82 60 51 47 44 40 40 44 47 43 36 33 39 47 51 48 45 42 40 40 40 38 32 28 29 34 35 27 17 12 11 11 10 9 10 14 18 22 37 75 116 137 139 126 95 70 70 87 96 93 74 58 54 50 42 34 34 41 46 44 38 35 39 47 49 43 36 32 30 30 31 29 26 23 25 30 31 27 21 16 14 12 10 9 11 15 19 23 37 72 83 97 100 93 73 57 59 73 81 81 68 59 59 53 42 35 37 46 52 50 44 43 47 54 56 46 32 24 20 19 19 18 16 16 19 25 29 29 27 23 18 14 11 9 11 14 18 23 33 54 61 69 71 66 56 48 51 61 66 68 60 55 59 58 48 44 51 64 72 70 65 64 64 70 71 56 37 23 17 13 11 10 9 11 15 22 28 33 34 31 25 17 13 11 11 14 19 26 33 45 57 63 63 58 53 51 52 53 52 51 46 47 58 68 66 65 75 90 98 96 91 90 87 89 90 74 49 29 19 13 9 8 8 9 14 21 29 36 41 39 32 23 17 13 13 15 22 32 40 48 62 70 72 69 70 70 66 55 45 40 38 45 65 89 97 99 105 111 109 101 96 97 95 99 103 88 60 36 23 15 11 10 10 11 14 20 29 36 41 41 36 27 20 16 14 16 25 37 46 53 66 80 88 90 93 94 84 65 47 39 40 51 78 111 128 132 135 131 118 103 94 93 92 95 98 85 59 37 24 18 15 15 15 15 16 20 25 29 32 33 31 27 22 18 15 16 23 35 45 53 60 75 86 91 96 96 84 64 46 39 42 53 76 107 124 130 136 136 127 112 102 98 94 90 88 74 51 33 23 19 20 23 25 25 23 20 21 21 22 23 24 23 21 19 16 17 22 32 41 49 47 55 64 70 76 76 66 50 37 33 35 42 55 75 86 93 103 116 122 118 113 109 101 89 79 65 44 29 22 20 23 29 36 39 34 26 22 20 19 19 19 19 19 19 20 22 26 33 39 42 37 38 43 47 50 50 45 35 27 24 26 29 34 43 49 55 67 86 101 105 105 102 92 79 69 57 42 30 25 24 28 36 44 49 45 37 32 30 26 23 22 19 19 22 26 29 33 37 40 39 33 31 32 32 31 30 28 22 18 17 20 22 23 26 28 32 42 57 69 74 75 74 68 60 54 50 46 41 36 34 37 43 51 58 57 52 50 49 40 32 27 22 19 22 28 32 35 40 41 38 34 30 28 26 22 21 19 17 14 14 15 17 18 19 21 24 31 40 46 47 47 47 44 42 43 50 60 64 57 48 46 50 56 63 65 62 62 61 49 37 28 22 18 19 23 28 34 42 46 42 39 32 29 25 21 19 18 16 15 13 14 15 16 17 19 23 28 33 35 34 32 30 28 29 36 56 84 98 88 68 58 54 54 57 59 57 56 55 46 36 28 22 19 18 20 25 35 49 57 51 43 36 33 29 26 24 22 20 18 16 15 15 16 17 21 26 30 31 31 30 28 25 23 23 31 57 93 116 109 89 77 69 59 55 54 51 47 44 38 33 27 22 19 19 20 26 38 55 64 57 48 47 48 47 47 47 41 32 26 24 21 19 20 22 26 32 35 33 31 31 31 29 27 25 29 48 75 95 96 91 90 83 69 60 57 51 42 36 32 29 25 21 20 20 22 27 37 53 62 57 61 73 84 88 95 98 86 64 49 44 39 35 34 34 38 44 47 42 37 35 36 37 36 33 34 42 53 62 68 74 80 77 65 57 53 46 37 30 28 27 26 24 25 26 27 29 36 48 56 57 71 97 116 127 144 155 140 106 83 73 64 57 55 55 58 65 67 61 52 47 46 48 50 49 50 51 46 43 45 52 58 56 48 43 40 36 30 27 28 30 31 31 33 35 33 31 33 39 45 54 63 88 106 120 145 167 159 128 107 95 79 70 71 78 85 90 90 84 75 66 63 65 69 72 74 71 53 37 33 37 41 40 36 33 30 28 26 27 32 36 36 36 38 40 39 35 32 32 35 44 57 75 87 98 121 146 145 126 112 100 80 73 85 106 117 118 113 106 97 86 77 76 80 86 90 84 59 37 31 35 39 40 38 36 33 30 30 32 36 39 38 37 40 43 45 43 38 32 32 40 64 80 86 88 101 117 118 106 98 87 70 70 93 124 137 135 126 121 115 100 80 70 73 81 86 80 59 41 37 43 50 51 50 47 42 38 37 37 38 38 37 36 38 43 48 49 44 37 36 45 81 98 100 94 93 101 103 98 91 83 71 73 94 118 127 125 125 131 133 114 79 58 55 62 67 64 51 42 46 58 66 65 59 53 46 40 37 37 39 40 40 38 37 38 42 44 43 40 43 57 108 130 128 111 97 99 108 113 110 104 91 88 96 103 103 102 112 134 147 126 81 49 39 40 43 43 40 41 52 69 79 74 61 52 44 35 30 33 41 47 47 44 39 36 36 37 40 46 55 76 136 163 158 131 104 102 118 133 133 127 111 102 100 96 89 87 100 131 150 128 79 43 29 27 28 31 33 39 54 73 82 75 60 50 42 31 26 30 42 51 52 49 43 37 34 34 41 53 68 95 "
	# with open("bot." + "debug", "a") as f:
		# logger.debug("Growth at %s" % getframeinfo(currentframe()).lineno)
		# objgraph.show_growth(limit=20,file=f)
		
	
	####### Idle exploration
	logger.debug("last_map is %s" % last_map)
	if last_map:
		logger.debug("Going to explore!")
		global explore_queue
		explore_queue = deque([loc for loc in last_map.getTerritory().fringe if not any([n.enemies for n in last_map.neighbors(loc, 1, True)])])
		#not sure if I want to reuse the queue from last turn or just do a new one
		gotFrameLock.acquire()
		explore_thread = threading.Thread(target=explore, daemon = True)
		explore_thread.start()
		
		thread_pair = (explore_thread, gotFrameLock)
		
		# gotFrameLock is released immediately after the string comes in
		# otherwise, the locations get screwed up by the incoming gameMap
		gameMap = getFrame(test_frame, thread_pair )	
		
		
	else:
		gameMap = getFrame(test_frame)
	
	gameMap.turnCounter = turnCounter
	
	take_turn(gameMap)
	# objgraph.show_backrefs([gameMap], filename='sample-%s.png' % turnCounter)
	
	
	
	# with open("bot." + "debug", "a") as f:
		# logger.debug("Growth at %s" % getframeinfo(currentframe()).lineno)
		# objgraph.show_growth(limit=20,file=f)
	
	# try:
		# objgraph.show_backrefs([gameMap], filename='sample-%s.png' % turnCounter)
	# except Exception as e:
		# logger.debug(e)
		# pass
	logger.debug("****** END TURN %d\t\t(time=%s) ******" % (turnCounter,timer()-gameMap.clock))
	# logger.debug("****** GC DONE %d\t(time=%s) ******" % (turnCounter,timer()-gc_time))
	last_map = gameMap
	gameMap.clock = None
	

# testBot()
		

# with open("bot." + "debug", "a") as f:
	# logger.debug("Growth at %s" % getframeinfo(currentframe()).lineno)
	# objgraph.show_growth(limit=20,file=f)
getInit(getString)
sendInit("ewirkerman")

turnCounter = -1
last_map = None

def main_loop():
	global last_map
	profiling = True
	profiling = False
	#import objgraph

	if not profiling:
		while True:
			main()
	else:
		if os.path.exists("stats"):
			if os.path.exists("stats\*"):
				os.remove("stats\*")
		while True:
			currpath = 'stats\mybot-%s.stats' % (turnCounter+1)
			cProfile.run('main()', currpath)
				

# with open("bot." + "debug", "a") as f:
	# logger.debug("Baselining at %s" % getframeinfo(currentframe()).lineno)
	# objgraph.show_growth(limit=20,file=f)
main_loop()