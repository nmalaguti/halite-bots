import sys
import os
sys.path = sys.path + [os.path.dirname(os.path.realpath(__file__))]

from hlt2 import *
from networking2 import *
from moves import *
import logging
import time
from math import pi
import cProfile
import pstats
import os
import balance
import vmprof
from claim import *
from timeit import default_timer as timer

##### Logging Setup
import logging
logger = logging.getLogger('bot')

base_formatter = logging.Formatter("%(message)s")
log_file_name = 'bot.'+'debug'
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
	
### when total production is low, should be favor sites with lower strenght
### when total production is high, should be favor sites with higher production

def claims_to_map(title, turnCounter, claims):
	m_d = {}
	for claim in claims:
		m_d.update(claim.build_map_dict())
	if m_d:
#		logger.debug("%s %s: %s" % (title, turnCounter, getMoveMap(move_dict = m_d)) )
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
#		# logger.debug("\nIterating over gen: %s\n" % debug_list(curr_wave))
		next_wave = []
		for loc in curr_wave:
#			# logger.debug("Iterating over %s with owner %s" % (loc, loc.site.owner) )
			if owner == 0:
				raise Exception("Not yet implemented")
				pass
			elif owner != gameMap.playerTag:
				for move in loc.site.enemies:
					child_loc = move.loc
					if child_loc.site.owner == owner:
						if child_loc not in done:
							done.add(child_loc)
							next_wave.append(child_loc)
#							# logger.debug("Iteration child %s" % child_loc)
			else:
				for move in loc.site.friends:
					child_loc = move.loc
					if child_loc not in done:
						done.add(child_loc)
						next_wave.append(child_loc)
#						# logger.debug("Iteration child %s" % child_loc)
			
			if func:
				r = func(loc)
				if r is not None:
					results.append(r)
			# pass
			
			# logger.error("")
			if limit and (timer() - gameMap.clock) > time_limit:
				logger.error("Aborting turn due to time limit! curr_time = %s" % (time.time() - gameMap.clock))
				return results
		curr_wave = next_wave
	return results

def take_turn(gameMap):
	
	######################################## BOT OPTIONS
	gameMap.multipull = False
	gameMap.chunkedpull = gameMap.multipull and True
	gameMap.breakthrough = True
	gameMap.breakthrough_range = 5
	gameMap.trail_search_distance_max = 7
	gameMap.gen_cap = False
	########################################
	
	
	
	
	
	
#	logger.debug("****** START TURN %d ******" % turnCounter)
	
	t = gameMap.getTerritory()
	center = t.getCenter()
	
	mf = MoveFork(gameMap, t.territory)
	
	######## Root Claim Generation
	
	all_capped_claims = []
	all_uncapped_claims = []
	all_capped_claims = []
	for loc in t.fringe:
		if not any([n.enemies for n in gameMap.neighbors(loc, 1, True)]):
			c_claim = CappedClaim(gameMap, loc)
			all_capped_claims.append(c_claim)
	
	
	
	#### Balancing of uncapped values
	percentile_capped_list = list(all_capped_claims)
	heapq.heapify(percentile_capped_list)
	keep_top_percent = .2
	for	i in range(int(len(all_capped_claims)*keep_top_percent)):
		heapq.heappop(percentile_capped_list)
	gameMap.target_uncapped_value = percentile_capped_list and percentile_capped_list[0].value or 1
#	# logger.debug("target_uncapped_value = %s" % gameMap.target_uncapped_value)
	
	enemy_roots = set()
	for terr in gameMap.getEnemyTerritories():
		for loc in terr.fringe:
			if loc.site.strength == 0 or loc.site.friends:
				enemy_roots.add(loc)
	
	for loc in enemy_roots:
		claim = UncappedClaim(gameMap, loc)
#		logger.debug("Created enemy_fringe claim: %s" % claim)
		all_uncapped_claims.append(claim)
	
	all_capped_claims = [claim for claim in all_capped_claims if claim.value > 0]
	
	
	
	####### Natural separation if there are no outside threats
	all_uncapped_claims = [claim for claim in all_uncapped_claims if claim.value > 0]
	if not all_uncapped_claims:
		for claim in all_capped_claims:
			uc_claim = UncappedClaim(gameMap, claim.loc)
			uc_claim.value = .0000001
			all_uncapped_claims.append(uc_claim)
	
	
	
	
#	logger.debug("\nall_capped_claims: \n%s" % "\n".join([claim.__str__() for claim in all_capped_claims]) )
#	logger.debug("\nall_uncapped_claims: \n%s" % "\n".join([claim.__str__() for claim in all_uncapped_claims]) )
	
	
	######## Claim Spreading
	
	for l in [list(all_uncapped_claims), list(all_capped_claims)]:
		for claim in l:
			claim.site.heap.add_claim(claim)
		working_list = [c for c in l if c.is_top_claim()]
		while working_list:
			for claim in working_list:
#				# logger.debug("")
#				# logger.debug("Expanding seed %s claim %s" % (claim.max_gen, claim) )
				claim.spread()
				
			working_list = [c for c in l if c.still_expanding]
#		logger.debug("\n\n")
		# for root in l:
			# move_dict = {}
			# if root.max_gen < 1:
				# continue
			# for gen in root.gens.values():
				# for claim in gen.claims:
					# move_dict[claim.loc] = claim.gen
			# logger.info("root map %s: %s" % (turnCounter, getMoveMap(move_dict = move_dict)))
#	logger.debug("Spread map %s: %s" % (turnCounter, getMoveMap(layered_iteration(t.fringe, gameMap, get_planned_move))))

	
	
	
	
#	logger.debug("I really think I want to start undoing claims if they can't finish and there is a different capped claim underneath them")
	
	# for root in all_capped_claims + all_uncapped_claims:
		# move_dict = {}
		# for gen in root.gens.values():
			# for claim in gen.claims:
				# move_dict[claim.loc] = "%s%s" % ("S^>v<"[claim.get_parent_direction()],int(claim.value*1000))
#		# logger.debug("root map %s %s:%s" % (turnCounter, root.loc, root.max_gen and getMoveMap(move_dict = move_dict) or "None"))
		# if root.max_gen:
			# pass
		
	
	
	######## Claim Move Filtering
		
#	logger.debug("")
#	logger.debug("Getting capped root claims as moves of %s's territory" % t.owner)
	
	layered_iteration(t.fringe, gameMap, find_loc_move, sort_func = gameMap.num_non_friends, limit=True)
	moves = layered_iteration(t.frontier, gameMap, get_loc_move)
	
	
	# # showing inbound strength
	
	# loc_inbound = [check_inbound_damage(loc) for loc in list(t.fringe)+list(t.frontier) if loc.gameMap.get_enemy_strength(loc)]
	
	# logger.info("inb map %s: %s" % (turnCounter, getMoveMap(move_dict = dict(loc_inbound))))
	
#	logger.debug("Moves map %s: %s" % (turnCounter, getMoveMap(moves)))
	
	
	
	
	# if turnCounter == 20:
		# raise Exception("Test Frame Ended")
	mf.submit_moves(moves)
	mf.output_all_moves()
	
gameMap = None
def main():
	
	global turnCounter
	global gameMap
	
	turnCounter += 1
#	logger.debug("****** PREP TURN %d ******" % turnCounter)
	test_frame = None
	# test_frame = "1 2 1 0 2 1 5 0 11 2 3 0 2 1 7 0 7 2 4 0 3 1 6 0 3 2 2 0 2 2 4 0 2 1 7 0 3 2 2 0 2 2 3 0 5 1 2 0 1 1 2 0 2 2 3 0 2 2 2 0 10 1 1 0 1 2 5 0 1 2 2 0 11 1 6 0 2 2 2 0 10 1 6 0 1 2 3 0 9 1 6 0 2 2 3 0 9 1 6 0 1 2 2 0 9 1 1 0 1 1 5 0 2 2 1 0 1 1 1 0 2 1 2 0 3 1 6 0 3 2 4 0 2 1 2 0 3 1 6 0 4 2 3 0 2 1 2 0 3 1 6 0 4 2 4 0 1 1 7 0 1 2 2 0 5 2 3 0 1 2 6 0 11 2 1 0 2 2 6 0 14 2 6 0 12 2 1 0 1 2 6 0 11 2 1 0 1 2 8 0 10 2 1 0 4 87 9 6 86 74 81 88 78 4 6 0 159 7 4 0 3 6 12 6 112 119 103 6 8 46 63 65 60 80 123 114 8 15 0 94 6 8 4 72 180 172 133 16 15 18 119 121 110 142 202 186 5 0 55 128 124 24 0 119 186 160 122 25 4 22 214 202 161 175 213 185 0 6 0 125 121 18 105 140 135 119 5 5 35 3 6 189 135 24 143 116 159 0 109 125 131 14 7 106 83 3 58 28 27 3 3 3 6 14 16 61 40 103 133 152 161 85 0 56 0 64 0 109 38 122 17 61 4 12 6 16 81 117 157 181 172 75 132 4 0 84 36 5 158 7 30 11 21 4 3 14 116 140 174 181 147 61 82 40 119 125 0 18 8 48 10 199 6 24 16 121 122 129 173 170 115 7 0 55 99 101 23 5 18 7 179 230 19 95 18 124 130 118 138 150 110 255 51 61 3 0 37 0 3 58 250 134 34 106 6 78 88 81 74 86 3 0 87 12 72 33 4 113 96 9 0 0 114 123 80 60 65 63 8 6 4 103 119 112 119 57 0 124 128 6 90 116 186 202 142 110 121 119 27 49 0 122 172 180 140 21 40 121 125 3 254 69 185 213 175 161 202 214 0 8 5 0 160 186 106 0 73 131 125 109 0 0 116 143 14 135 189 4 3 12 10 5 119 135 56 0 0 161 152 133 103 0 0 6 49 113 0 3 6 18 15 0 78 83 0 95 75 172 181 157 117 81 31 4 6 4 100 12 91 0 95 45 167 2 20 10 61 147 181 174 140 116 0 15 24 21 0 30 0 6 20 24 0 12 55 21 48 115 170 173 129 122 255 0 12 30 30 40 8 32 87 0 2 119 14 51 65 110 150 138 118 130 124 3 6 12 32 20 42 18 0 3 6 99 "
	gameMap = getFrame(test_frame)
	clock = timer()
	gameMap.clock = clock
	gameMap.turnCounter = turnCounter
	
	take_turn(gameMap)
	
	logger.error("****** END TURN %d (time=%s) ******" % (turnCounter,timer()-gameMap.clock))
	
# testBot()
		
		
myID, gameMap = getInit(getString)
sendInit("UnpypyBot")

turnCounter = -1

def main_loop():
	while True:
		profiling = True
		# profiling = False
		if not profiling:
			main()
		else:
			currpath = 'stats\mybot-currturn.stats'
			lastpath = 'stats\mybot-lastturn.stats'
			
			if os.path.exists(currpath):
				if os.path.exists(lastpath):
					os.remove(lastpath)
				os.rename(currpath, lastpath)
			cProfile.run('main()', currpath)
			# if "pypy" not in sys.executable:
			# else:
				# with open(currpath, "w") as f:
					# vmprof.enable(f.fileno(), period=0.00099, memory=False)
					# main()
					# vmprof.disable()
				
#cProfile.run('main_loop()', 'stats\mybot.stats')
main_loop()
