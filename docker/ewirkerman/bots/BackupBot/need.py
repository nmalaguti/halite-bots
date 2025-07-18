from hlt2 import *
from networking2 import *
from moves import *

import itertools
import heapq
import logging

need_logger = logging.getLogger('need')
base_formatter = logging.Formatter("%(asctime)s : %(levelname)s %(message)s")
log_file_name = 'bot.debug'
hdlr = logging.FileHandler(log_file_name)
hdlr.setFormatter(base_formatter)
hdlr.setLevel(logging.DEBUG)
need_logger.addHandler(hdlr)
need_logger.setLevel(logging.DEBUG)

import copy

class NeedError(Exception):
	pass


class Need:
	def __init__(self, site, map, loc_pool):
		self.gameMap = map
		self.site = site
		self.production = 0
		self.strength = 0
		self.loc_pool = loc_pool
		self.already_used = []
		self.moves = []
		self.generations = 0
		enemy_str = 0
		for move in self.site.enemies:
			for site in move.getSites():
				enemy_str += site.strength
		self.effective_str = self.site.strength + enemy_str
		need_logger.error("Need %s for %s at %s" % (site.strength, site.production,site.loc))
		
	def is_satisfied(self):
		met = self.strength > self.effective_str
		need_logger.debug("Help %s > Need %s? %s" %(self.strength, self.site.strength, met))
	
		return met
	
	def check_generation(self, generation):
		next_gen = []
		this_gen = []
		met = None
		need_logger.debug("Already used: %s" % debug_list(self.already_used) )
		need_logger.debug("Generation: %s" % debug_list(generation))
		
		gen_strength = 0
		gen_production = 0
		for move in generation:
			site = self.gameMap.getSite(move.loc)
			gen_strength += site.strength
			gen_production += site.production
			
			# Analyze the possible sets to get the min possible
			for friend_move in site.friends:
				if not friend_move.loc in [used.loc for used in self.already_used] and not friend_move.loc in [used.loc for used in next_gen] and friend_move.loc in self.loc_pool and site.strength + site.production + friend_move.loc.site.strength < 255:
					next_gen.append(friend_move)
				
		need_logger.debug("%s + %s > %s?" % (self.strength, gen_strength,self.effective_str) )
		if self.strength + gen_strength > self.effective_str:
			combos = []
			
			for i in range(1, len(generation) + 1):
				combo = [move for move in next(itertools.combinations(generation, i))]
				logger.debug("Combination: %s" % combo)
				combo_strength = sum([move.loc.site.strength for move in combo])
				combo_production = sum([move.loc.site.production for move in combo])
				heapq.heappush(combos, ( self.strength + combo_strength - self.effective_str + combo_production/100, combo))

			logger.debug("Combinations: %s" % debug_list(combos))
			best = combos[0]
			for combo_tup in combos:
				if combo_tup[0] > 0:
					best = combo_tup
				
			logger.debug("Best Combination: %s" % debug_list(best))
			logger.debug("Best Moves: %s" % list(best[1]))
			for move in list(best[1]):
				logger.debug("Move: %s" % move)
				site = move.loc.site
				need_logger.debug("Pledged %s from %s" % (site.strength,move.loc))
				self.production += site.production
				self.strength += site.strength
				this_gen.append(move)
			met = True
		else:
			this_gen.extend(generation)
			self.production += gen_production
			self.strength += gen_strength
			met = False
			
		return met, this_gen, next_gen
		
		
	def get_moves(self):
		if not self.moves:
			gen = []
			need_logger.debug("Seeding from : %s" % debug_list(self.site.friends) )
			# This sets up the base generation from the location we are targeting
			for friend_move in self.site.friends:
				if not friend_move.loc in [used.loc for used in self.already_used] and not friend_move.loc in [used.loc for used in gen] and friend_move.loc in self.loc_pool:
					gen.append(friend_move)
			
			need_logger.debug("Seed gen: %s" % debug_list(gen) )
			need_logger.debug("Loc Pool: %s" % debug_list(self.loc_pool) )
			while len(gen) > 0:
				satisfied, this_gen, next_gen = self.check_generation(gen)
				self.generations += 1
				first = False
				if satisfied:
					need_logger.debug("Satisfied! %s" % debug_list(this_gen))
					self.moves = [Move(m.loc, STILL) for m in self.already_used] + this_gen
					return self.moves
				else:
					need_logger.debug("Mid gen: %s" % debug_list(this_gen))
					self.already_used.extend(this_gen)
					need_logger.debug("Already_used list: %s" % debug_list(self.already_used))
					self.strength += self.production
					#if self.is_satisfied():
					#	return [Move(m.loc, STILL) for m in already_used]
					gen = next_gen
					
			self.moves = [Move(m.loc, STILL) for m in self.already_used]
			return self.moves
