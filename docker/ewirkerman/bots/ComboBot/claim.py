from hlt2 import *
from networking2 import *
from moves import *
import logging

#attack_logger = logging.getLogger('attack')
base_formatter = logging.Formatter("%(levelname)s %(message)s")
log_file_name = 'bot.debug'
hdlr = logging.FileHandler(log_file_name)
hdlr.setFormatter(base_formatter)
hdlr.setLevel(logging.DEBUG)
#attack_logger.setLevel(logging.ERROR)


def getStrFromMoveLoc(move):
	return move.loc.site.strength

class ClaimHeap:
	def __init(self, site):
		self.heap = []
		self.site = site
		site.heap = self
		
	def add_claim(self, claim):
		old_best = self.get_best_claim()
		heapq.heappush(claim.value())
		claim.heap = self
		new_best = self.get_best_claim()
		
		# if the best changes, then cancel the old best and issue the new best
		if old_best is not new_best:
			old_best.rescind()
			new_best.find_children()
		
	def remove_claim(self, claim):
		#self.children = [(c.value, c) for c in self.heap if c is not claim ]
		#heapq.heapify()
		heapq.heappop()
		claim.heap = self
		self.get_best_claim().find_children()
		
		
	def get_best_claim(self):
		# the items on the heap are actually tuples, but the caller doesn't need to care about that
		return self.heap[0][1]
		
		
class Claim:
	def __init__(self, map, location, cap = None, parent = None, dir=0, root = None):
		self.gameMap = map
		self.cap = cap
		self.parent = parent or self
		self.loc = location
		self.site = self.loc.site
		self.strength = 0
		self.production = 0
		self.value = balance.evaluateMapSite(site)
		self.children = []
		self.directionToParent = dir
		self.root = root or self
		self.last_descendants
		self.satisfied = False
		self.potential_strength = 0
		self.potential_production = 0
		
		root.add_descendant(self.parent, self)
		
	def is_satisfied(self):
		if cap:
			return strength > cap
		return False
		
	def rescind(self):
		#rescind all children
		for child in children:
			child.rescind()
		
		#inform the heap
		self.heap.remove_claim(self)
		
		#inform the parent that they can't use this claim anymore
		self.parent.remove_child(self)
		
	def remove_child(self, child):
		self.children = [c for c in self.children if c is not child ]
		self.find_children()
		
	def find_children(self, remaining):
		# This is where the meat of the child finding and combinations goes
		
		
		potential_children = [ create_child(dir) for dir in CARDINALS if dir != directionToParent ]
		
		for child in potential_children:
			self.potential_strength += child.site.strength
			self.potential_production += child.site.production
					
		if cap and balance.claim_complete_conditions(self):
			combos = []
			
			for i in range(len(potential_children)):
				i += 1
				combo = [claim for claim in next(itertools.combinations(potential_children, i))]
#				logger.debug("Combination: %s" % combo)
				try:
					combo_score = balance.evaluate_claim_combo(self, combo)
					heapq.heappush(combos, ( combo_score, combo))
				except ValueError:
#					claim_logger.debug("Invalid combination")
					pass
	
#			logger.debug("Combinations: %s" % debug_list(combos))
			best = combos[0]
			combo_tup = combos[0]
			self.children = combo_tup[1]
			root.satisfied = True
		else:
			self.children = potential_children
		
		for claim in self.children:
			claim.site.heap.add_claim(claim)
	
	def get_value(self):
		if self.is_satisfied():
			return 0
		return self.value
		
	def get_parent(self)
		return self.parent
		
	def get_children(self)
		return self.children
		
	def create_child(self, direction):
		child = Claim(self.map, map.getLocation(self.location, direction), parent = self, dir = util.getOppositeDir(direction), root = self.root)

	def add_descendant(self, parent, child):
		try:
			self.last_descendants.remove(parent)
		except ValueError:
			pass # or scream: thing not in some_list!
		self.last_descendant.append(child)
	
	# Should really only be used on the root	
	def get_last_descendants(self):
		return self.last_descendants
	
	# Should really only be used on the root
	def get_as_moves(self):
		if cap:
			return [Move(child.site.loc, child.dir) for child in self.get_last_descendants()]
		my_move = []
		if self is not root and balance.claim_move_conditions(self):
			my_move.append(Move(self.site.loc, self.directionToParent))
		return [move for child in children for move in child.get_as_moves()] + my_move
