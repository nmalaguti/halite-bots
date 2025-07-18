from hlt2 import *
from networking2 import *
from moves import *

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
		#need_logger.error("Need %s for %s at %s" % (site.strength, site.production,site.loc))
		
	def is_satisfied(self):
		enemy_str = 0
		for move in self.site.enemies:
			for site in move.getSites():
				enemy_str += site.strength
		effective_str = max
		met = self.strength > max([self.site.strength, enemy_str])
		#need_logger.debug("Help %s > Need %s? %s" %(self.strength, self.site.strength, met))
	
		return met
	
	def check_generation(self, generation):
		next_gen = []
		this_gen = []
		met = None
		#need_logger.debug("Already used: %s" % debug_list(self.already_used) )
		#need_logger.debug("Generation: %s" % debug_list(generation))
		for move in generation:
			site = self.gameMap.getSite(move.loc)
			self.apply_help(move.loc, site)
			this_gen.append(move)
			
			for friend_move in site.friends:
				if not friend_move.loc in [used.loc for used in self.already_used] and not friend_move.loc in [used.loc for used in next_gen] and friend_move.loc in self.loc_pool:
					next_gen.append(friend_move)
			met = self.is_satisfied()
			if met:
				break
		return met, this_gen, next_gen
		
		
	def get_moves(self):
		if not self.moves:
			gen = []
			#need_logger.debug("Seeding from : %s" % debug_list(self.site.friends) )
			# This sets up the base generation from the location we are targeting
			for friend_move in self.site.friends:
				if not friend_move.loc in [used.loc for used in self.already_used] and not friend_move.loc in [used.loc for used in gen] and friend_move.loc in self.loc_pool:
					gen.append(friend_move)
			
			#need_logger.debug("Seed gen: %s" % debug_list(gen) )
			#need_logger.debug("Loc Pool: %s" % debug_list(self.loc_pool) )
			while len(gen) > 0:
				satisfied, this_gen, next_gen = self.check_generation(gen)
				first = False
				if satisfied:
					#need_logger.debug("Satisfied! %s" % debug_list(this_gen))
					self.moves = [Move(m.loc, STILL) for m in self.already_used] + this_gen
					return self.moves
				else:
					#need_logger.debug("Mid gen: %s" % debug_list(this_gen))
					self.already_used.extend(this_gen)
					#need_logger.debug("Already_used list: %s" % debug_list(self.already_used))
					self.strength += self.production
					#if self.is_satisfied():
					#	return [Move(m.loc, STILL) for m in already_used]
					gen = next_gen
					
			self.moves = [Move(m.loc, STILL) for m in self.already_used]
			return self.moves
		
		
	def apply_help(self, loc, site):
		#need_logger.debug("Pledged %s from %s" % (site.strength,loc))
		
		self.production += site.production
		self.strength += site.strength
		