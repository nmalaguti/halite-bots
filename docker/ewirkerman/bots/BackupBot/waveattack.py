from hlt2 import *
from networking2 import *
from moves import *
import logging

attack_logger = logging.getLogger('attack')
base_formatter = logging.Formatter("%(levelname)s %(message)s")
log_file_name = 'bot.debug'
hdlr = logging.FileHandler(log_file_name)
hdlr.setFormatter(base_formatter)
hdlr.setLevel(logging.DEBUG)
attack_logger.setLevel(logging.ERROR)


def getStrFromMoveLoc(move):
	return move.loc.site.strength

class Wave:
	def __init__(self, loc_pool, already_waved=None):
		self.loc_pool = loc_pool
		#attack_logger.debug("Incoming Already_waved %s" % debug_list(already_waved))
		if not already_waved is None:
			self.already_waved = already_waved
		else:
			self.already_waved = set()
		#attack_logger.debug("New Wave Already_waved %s" % debug_list(self.already_waved))
		self.moves = {}
		self.strength = 0
		self.next = None
		
	def bootstrap(self, locs):
		for loc in locs:
			self.addMove(Move(loc, 0))
		
	def getNext(self):
		if not self.next:
			#attack_logger.debug("Generating next wave")
			self.next = Wave(self.loc_pool, self.already_waved)
			for move in self.getMoves():
				site = move.loc.site
				#attack_logger.debug("Checking friend moves from %s" % move.loc)
				
				for friend_move in site.friends:
					#attack_logger.debug("Wave candidate %s" % friend_move)
					#attack_logger.debug("Already_waved %s" % debug_list(self.already_waved))
					if not friend_move.loc in self.already_waved and friend_move.loc in self.loc_pool:
						self.next.addMove(friend_move)
			
			#attack_logger.debug("Updating Already_waved with %s" % debug_list(self.next.getLocs()))
			self.already_waved.update(self.next.getLocs())
			#attack_logger.debug("New Already_waved with %s" % debug_list(self.already_waved))
		return self.next
		
	def addMove(self, move):
		#attack_logger.debug("Adding %s to %s" % (move, self))
		if not move.loc in self.moves:
			self.strength += move.loc.site.strength
			self.moves[move.loc] = Move(move.loc, move.getDirections())
		self.moves[move.loc].addDirection(move.getDirections())
		
	def getMoves(self):
		return list(self.moves.values())
		
	def getLocs(self):
		return list(self.moves.keys())
	
	def getStrength(self):
		return self.strength
		
	def __str__(self):
		return debug_list(self.getMoves())
	
class WaveAttack:
	def __init__(self,map):
		self.gameMap = map
	
	def getAggressionFactor(self, seed):
		#return next(iter(seed)).site.strength > 0 and 10 or 5
		return 5
		
	def getSplitWave(self, waves):
	
		return ((len(waves)+1) / 2)
		my_str = self.gameMap.getTerritory().strength
		total_str = sum([t.strength for t in  self.gameMap.getTerritories()])
		
#		target_str = my_str * my_str/(1+total_str)
		target_str = my_str / 4
		logger.debug("My strength: %s" % my_str)
		logger.debug("Enemy strength: %s" % (1+total_str-my_str))
		logger.debug("Target strength: %s" % target_str)
		for i in range(len(waves)):
			target_str -= waves[i].getStrength()
			logger.debug("Target strength remaining after wave %s: %s" % (i,target_str) )
			if target_str < 0:
				return i + 1
		return 1000
			
		
		
	def getSpreadWave(self, waves):
		return 1
		
	def get_moves(self, seed, loc_pool, turn_count):
		if not seed:
			return [],[]
		waves = []
		wave = Wave(loc_pool)
		wave.bootstrap(seed)
		wave = wave.getNext().getNext()
		#attack_logger.debug("Seed Wave %s:" % (wave))
		
		# All of the waves in a given Attack (which is one per turn anyway) share the same "Already_Waved"
		while (len(wave.getMoves()) > 0):
			waves.append(wave)
			new_wave = wave.getNext()
			wave = new_wave
				
		freq = 3
		i = 0
		aggression_divisor = 3
		split_wave = self.getSplitWave(waves)
		spread_wave = self.getSpreadWave(waves)
		preneeds = []
		postneeds = []
		for i in range(len(waves)):
			if i < split_wave:
				moves = preneeds
			else:
				moves = postneeds
			#attack_logger.debug("Sending wave %s:%s" % (i, waves[i]))
			
			for move in sorted(waves[i].getMoves(), key=getStrFromMoveLoc, reverse=True):
				site = self.gameMap.getSite(move.loc)
				if site.strength > site.production * self.getAggressionFactor(seed) :
					if self.gameMap.playerTag == 1 and ((move.loc.x % 2 == move.loc.y % 2) == (self.gameMap.turnCounter % 2 == 0)) and i < spread_wave:
						#attack_logger.debug("Not the right square color, skipping %s" % move.loc)
						pass
					else:
						moves.append(move)
					
				else:
					moves.append(Move(move.loc, 0))
		
		return preneeds,postneeds