from hlt2 import *
from networking2 import *
from moves import *
import logging

attack_logger = logging.getLogger('attack')
base_formatter = logging.Formatter("%(asctime)s : %(levelname)s %(message)s")
log_file_name = 'bot.debug'
hdlr = logging.FileHandler(log_file_name)
hdlr.setFormatter(base_formatter)
hdlr.setLevel(logging.DEBUG)
attack_logger.setLevel(logging.DEBUG)


def getStrFromLoc(move):
	return move.loc.site.strength

class Attack:
	def __init__(self,map):
		self.gameMap = map
		
	def is_satisfied(self):
		met = self.strength > self.site.strength
		return met
	
	def create_wave(self, wave, already_waved, frontier, loc_pool, past_frontier):
		next_wave = {}
#		attack_logger.info("Creating a wave from (past_frontier = %s): %s" % (past_frontier, debug_list(wave)))
#		attack_logger.debug("Excluding: %s" % debug_list(already_waved))
		
		
		found_internal = False
		for move in wave:
#			attack_logger.debug("Getting friends of: %s" % move.loc)
			site = self.gameMap.getSite(move.loc)
			
			for friend_move in site.friends:
#				attack_logger.debug("Checking friend of: %s" % friend_move.loc)
				fsite = self.gameMap.getSite(friend_move.loc)
				if not friend_move.loc in frontier:
					found_internal = False
#				attack_logger.debug("str(%s) internal = %s, already = %s" % (fsite.strength,not friend_move.loc in frontier,friend_move.loc in already_waved) )
				if (not past_frontier or not friend_move.loc in frontier) and not friend_move.loc in already_waved and friend_move.loc in loc_pool:
#					attack_logger.debug("Adding a Move: %s, %s" % (friend_move.loc, friend_move.getDirections()))
					if not friend_move.loc in next_wave:
						next_wave[friend_move.loc] = Move(friend_move.loc, friend_move.getDirections())
					next_wave[friend_move.loc].addDirection(friend_move.getDirections())
					
		already_waved.update(next_wave.keys())

#		attack_logger.info("Next wave: %s" % (debug_list(next_wave)) )
		return next_wave.values(), found_internal
	
	def getAggressionFactor(self):
		return 5
		
	def get_moves(self, seed, frontier, loc_pool, turn_count):
		waves = []
		already_waved = set()
		past_frontier = False
		wave = []
		for loc in seed:
			site = loc.site
			for friend_move in site.friends:
				if not friend_move.loc in already_waved:
					already_waved.add(friend_move.loc)
					wave.append(friend_move)
				
		
		
		
		while (len(wave) > 1): # and not any([self.gameMap.getTerritory(self.gameMap.playerTag).getCenter() == m.loc for m in wave])):
			new_wave, found_internal = self.create_wave(wave, already_waved, frontier, loc_pool, past_frontier)
			past_frontier = found_internal
#			attack_logger.info("Appending a Wave: %s" % debug_list(new_wave))
			waves.append(new_wave)
			wave = new_wave
		
#		for i in range(len(waves)):
#			attack_logger.info("Wave %s: %s" % (i, debug_list(waves[i])))
		
		if len(waves) > 0:
			self.gameMap.attackCenters = [move.loc for move in waves[-1]]
		
#		attack_logger.debug("Attack Map: " + getMoveMap(self.gameMap,[item for sublist in waves for item in sublist]))
		
		freq = 3
		i = 0
		aggression_divisor = 2
		
		split = len(waves)/aggression_divisor
		early_moves = []
		late_moves = []
		for i in range(len(waves)):
			if i < split:
				moves = early_moves
			else:
				moves = late_moves
#			attack_logger.debug("Sending wave %s:" % i)
			for move in sorted(waves[i], key=getStrFromLoc, reverse=True):
				site = self.gameMap.getSite(move.loc)
				if site.strength > site.production * self.getAggressionFactor() :
					#attack_logger.debug("%s  vs %s" % (move.loc, self.gameMap.turnCounter))
					if self.gameMap.playerTag == 1 and ((move.loc.x % 2 == move.loc.y % 2) == (self.gameMap.turnCounter % 2 == 0)) and i < 1:
						#attack_logger.debug("Not the right square color, skipping %s" % move.loc)
						pass
					else:
						moves.append(move)
					
				else:
					moves.append(Move(move.loc, 0))
		
		return early_moves,late_moves