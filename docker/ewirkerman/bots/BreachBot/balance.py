from util import *
from hlt2 import Location
import logging
	
theMap = None
def evaluateMapSite(myMap):
	global theMap
	theMap = myMap
	return evaluateSite
	
def evalSiteProduction(site, claim = None):
	if not site.loc in site.loc.gameMap.site_production_cache:
		p = site.production
		if site.enemies:
			p += sum([min([move.loc.site.strength, site.strength]) for move in site.enemies])
		site.loc.gameMap.site_production_cache[site.loc] = p
		
#	# logging.getLogger("bot").debug("evalSiteProduction = %s" % site.loc.gameMap.site_production_cache[site.loc])
	return site.loc.gameMap.site_production_cache[site.loc]
	
def evalSiteStrength(site):
	# should cache the answer of this
	
	# s = max([site.strength, 1]) ** 1
	return (site.strength or 1) 

def claim_combo__lt__(self, other):
	return self.value < other.value
	
def evaluate_claim_combo(claim_combo):
	# This should include a provision that minimizes the amount of production lost, minimizes the amount of overkill
#	# # logging.getLogger("bot").debug("Checking value parent %s of claim_combo : %s" % (claim_combo.parent, debug_list(claim_combo.claims)) )
	
	loss_avoidance = 1000
	strength = 0
	loss = 0
	p_loss = 0
	for claim in claim_combo:
		strength += claim.site.strength
		if claim.site.strength + claim.site.production > 255:
			p_loss -= claim.site.strength + claim.site.production - 255
		p_loss += claim.site.production
		
	if claim_combo.parent.site.owner == 0:
		strength -= claim_combo.parent.site.strength
#		#logging.getLogger("bot").debug("root strength=%s"% strength)
	else:
	
		# If the parent isn't moving, add its strength
		parent_still_str = claim_combo.parent.site.strength + claim_combo.parent.site.production
		if claim_combo.parent.site.heap.dir:
#			# logging.getLogger("bot").debug("Combo parent %s is moving out of the way" % claim_combo.parent)
			pass
		elif strength + parent_still_str < 255:
#			# logging.getLogger("bot").debug("No overflow projected - %s staying still" % claim_combo.parent)
			strength += parent_still_str
			pass
		elif parent_still_str < strength:
#			# logging.getLogger("bot").debug("Overflow projected - I'm switching because I'll be smaller, so don't count me here")
			pass
		else:
#			# logging.getLogger("bot").debug("Overflow, but %s bigger than the incoming, so staying STILL" % claim_combo.parent)
			strength += parent_still_str
			pass
#	# logging.getLogger("bot").debug("total strength=%s"% strength)	
	
	if strength > 255:
		loss = strength - 255
		strength = 255
#	# logging.getLogger("bot").debug("loss=%s"% loss)
#	# logging.getLogger("bot").debug("strength=%s"% strength)
	
	
	e_str = claim_combo.parent.e_str
	damage = claim_combo.parent.damage
	
	
	# e_str = claim_combo.parent.gameMap.get_enemy_strength(claim_combo.parent.loc)
	# damage = 0	
	# if e_str:
		# damage = claim_combo.parent.gameMap.get_enemy_strength(claim_combo.parent.loc, damage_dealable=True)
	
	
	all_capped = all([claim.is_capped() for claim in claim_combo.claims])
	
	
	# We are away from the enemy so the less strength we take it with, the better
	if claim_combo.parent.is_capped() and claim_combo.parent.is_root() and not e_str and all_capped:
		
		# if you don't have enough strength to take a strong neutral, this isn't a good combo
		# reminder that we subtracted the neutral's strength from "strength" to start
		if claim_combo.parent.site.strength > 1 and strength < 1:
#			# logging.getLogger("bot").debug("Not enough strength to take the neutral with combo: %s" % debug_list(claim_combo.claims))
			strength += 1024
		
		#any claim combo that doesn't have at least one moving to a 0 square is bad
		elif claim_combo.parent.site.strength < 1 and len(claim_combo) != 1:
#			# logging.getLogger("bot").debug("Wrong number for taking an empty: %s" % debug_list(claim_combo.claims))
			strength += 1024
			
		else:
#			# logging.getLogger("bot").debug("Able to capture a neutral with: %s" % debug_list(claim_combo.claims))
			pass
			
		value = (strength + loss_avoidance*loss)
		
		# as few capped claims as possible and as much uncapped as possible
		
	else:
		value = -1 * (strength + damage - loss_avoidance*loss - p_loss)
#	# logging.getLogger("bot").debug("Claim combo from %s has %s" % (debug_list(claim_combo.claims), value) )
	return value
	
	

def strength_limit(site):
	return 255
	
def uncapped_claim_benefit(claim):
	damage = 0
	for move in claim.site.enemies:
		site = move.loc.site
		damage += move.loc.site.strength
		
	
	if claim.is_root():
		ret = (1+claim.site.production/20000)*claim.gameMap.target_uncapped_value * (1 + damage/(1020*100000))
		# owner = None
		# for move in claim.root.site.enemies:
			# owner = move.loc.site.owner
			# break
		
		# m = claim.gameMap.getTerritory() 
		# t = claim.gameMap.getTerritory(owner) 
		# if t.strength < m.strength or t.production < m.production:
			# ret *= 2
		return ret
	else:
		return claim.root.benefit

	
	
def claim_combo_valid(claim_combo):
	return True
	
def claim_move_conditions(claim, parent = None):
	if not claim.get_parent():
		return False
	return claim_move_conditions_parentless(claim)

def claim_move_conditions_parentless(claim):
	if claim.loc.site.heap.dir:
		return True
	
	enemy_str = 0
	parent = claim.get_parent()
	# Never move a tile you don't own
	if claim.site.owner != claim.gameMap.playerTag:
		ret = False

	elif claim.root.is_capped():
		ret = send_gen_conditions(claim, claim.gen) # threshhold
#		logging.getLogger("bot").debug("claim %s vs root_max %s" % (claim.gen, claim.root.max_gen)  )
		
		
	else: # UNCAPPED
		if claim.gen == 1 and claim.root.site.strength > 0:
			if claim.gameMap.breakthrough and claim.site.strength > parent.site.strength:
			
				owner = None
				for move in parent.site.enemies:
					owner = move.loc.site.owner
					break
				
				m = claim.gameMap.getTerritory() 
				t = claim.gameMap.getTerritory(owner) 
#				logging.getLogger("bot").debug("Checking for breakthrough: ms:%s\tts:%s\tmp:%s\ttp:%s" %(m.strength, t.strength, m.production, t.production))
				if t.strength < m.strength and t.production < m.production:
					ret = True
					claim.root.breakthrough = True
#					logging.getLogger("bot").debug("Success - %s will breakthrough %s!" % (claim.loc, claim.loc))
				elif t.strength < m.strength or t.production < m.production:
					local_e = claim.gameMap.get_friendly_strength(loc=parent.loc, dist=5, type="enemies")
					local_m = claim.gameMap.get_friendly_strength(loc=parent.loc, dist=4, type="friends")
#					logging.getLogger("bot").debug("Checking for local breakthrough: ms:%s\tts:%s" %(local_m, local_e))
					if local_m - parent.site.strength > local_e * 2:
						ret = True
						claim.root.breakthrough = True
#						logging.getLogger("bot").debug("Success - %s will breakthrough %s!" % (claim.loc, parent.loc))
					else:
						ret = False
#						logging.getLogger("bot").debug("Failed to breakthrough")
				else:
#					logging.getLogger("bot").debug("Failed to breakthrough")
					ret = False
				
				
			else:
				ret = False
		else:
			# This I feel is a hack because I should just be able to count the incoming strength and beat it
			# ret = claim.site.strength > claim.site.production*7
			ret = True

#	# logging.getLogger("bot").debug("Will %s (parent: %s) move (e_str = %s)? %s" % (claim, parent, enemy_str, ret) )
	return ret
	# Uncapped requirement for MOVE

def send_gen_conditions(claim, gen_index = None):
	if not claim.gameMap.multipull:
		return claim.gen == claim.root.max_gen and claim_complete_conditions(claim.root)

	gen = claim.root.gens[gen_index]
	if claim.root.ancestors < 1:
#		# logging.getLogger("bot").debug("%s has no ancestors" % claim.root)
		return False
	
	prec = gen.get_preceding_str()
	
#	# logging.getLogger("bot").debug("%s is check_strength_threshhold to cover cap %s?" % (prec, gen.strength) )
	if claim.root.trail.check_strength_threshhold(prec, gen.strength): 
#		# logging.getLogger("bot").debug("%s has enough to cover cap %s " % (claim.root, claim.root.cap) )
		return True
		
#	logging.getLogger("bot").debug("%s incomplete" % (claim.root) )
	return False	
	
def claim_complete_conditions(claim, this_gen_str = 0, this_gen_production = 0):
	if claim.ancestors < 1:
#		# logging.getLogger("bot").debug("%s has no ancestors" % claim)
		return False
		
	# raise Exception("This prevents uncapped claims from pulling onto populated neutrals")
	if not claim.is_capped():
#		# logging.getLogger("bot").debug("%s is not capped" % claim)
		if claim.cap:
			return claim.cap < claim.strength + this_gen_str
		return False
		
#	# logging.getLogger("bot").debug("%s is enough to cover cap %s?" % (claim.strength, claim.cap) )
	if claim.gameMap.multipull and claim.strength + this_gen_str > claim.trail.threshholds[-1]: 
		return True
	elif claim.strength + this_gen_str > claim.cap: 
#		# logging.getLogger("bot").debug("%s has enough to cover cap %s " % (claim, claim.cap) )
		return True
		
	if not claim.strength and not claim.cap:
#		# logging.getLogger("bot").debug("%s has 0 and needs 0" % (claim) )
		return True
#	# logging.getLogger("bot").debug("%s incomplete" % (claim) )
	return False
