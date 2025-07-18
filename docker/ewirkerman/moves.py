from util import *
import logging
import copy
import weakref
from random import shuffle
import balance


moves_logger = logging.getLogger("moves")

try :
	if moves_logger and len(moves_logger.handlers) < 1:
		base_formatter = logging.Formatter("%(levelname)s %(message)s")
		log_file_name = 'bot.debug'
		hdlr = logging.FileHandler(log_file_name)
		hdlr.setFormatter(base_formatter)
		hdlr.setLevel(logging.DEBUG)
		moves_logger.addHandler(hdlr)
		moves_logger.setLevel(logging.DEBUG)
except NameError:
	pass


STILL = 0
CARDINALS = [1,2,3,4]

def moveCharLookup(dirs):
	if type(dirs) is int:
		return "%s" % dirs
	
	#moves_logger.debug("Getting move char from %s" % dirs)
	if len(dirs) == 1:
		if 1 in dirs:
			char = "^"
		elif 2 in dirs:
			char = ">"
		elif 3 in dirs:
			char = "v"
		elif 4 in dirs:
			char = "<"
		elif 0 in dirs:
			char = "."
	elif len(dirs) == 2:
		if 1 in dirs:
			if 2 in dirs:
				char = "7"
			elif 3 in dirs:
				char = "|"
			elif 4 in dirs:
				char = "r"
		elif 2 in dirs:
			if 3 in dirs:
				char = "J"
			elif 4 in dirs:
				char = "-"
		elif 3 in dirs:
			char = "L"
	elif len(dirs) == 3:
		if not 1 in dirs:
			char = "U"
		elif not 2 in dirs:
			char = "["
		elif not 3 in dirs:
			char = "n"
		elif not 4 in dirs:
			char = "]"
	elif len(dirs) == 4:
		char = "+"
	else:
		char = "X"
		
	#moves_logger.debug("Found: %s" % char)
	return " %s " % char


def setMapChar(move_dict, move):
	char = " X "
	# moves_logger.debug("Move options: %s" % move)
	loc = move.loc
	dirs = set(move.getDirections())
	
	if loc.site().owner == loc.gameMap().playerTag:
		move_dict[loc] = moveCharLookup(dirs)
	else:
		move_dict[loc] = "?"
	

def getMoveMap(moves = None, gameMap=None, move_dict = None, func=setMapChar):
	s = "\n"
	t = gameMap.getTerritory(gameMap.playerTag)
	
	if moves is None:
		moves = []
	
	if move_dict is None:
		move_dict = {}
		for move in moves:
			func(move_dict, move)
		
	if not move_dict:
		return
	#move_dict = {move.loc: move.direction for move in moves}
	
	# Header row
	for j in range(len(gameMap.contents[0])):
		s = "%s\t%d" % (s,j)
	s = "%s\n" % s
	
	for i in range(len(gameMap.contents)):
		# Header column
		row = gameMap.contents[i]
		s = "%s%d" % (s,i)
		
		for j in range(len(row)):
			column = row[j]
			
			#### This sets the display value of the gameMap
			l = gameMap.getLocationXY(j,i)
			site = l.site()
			if l in t.fringe and site.strength == 0:
				column = "_"
			elif l in move_dict:
				column = move_dict[l]
			elif l in t.territory:
				column = " . "
			
			####
			s = s + "\t" + str(column)
		s = s + "\t%s\n" % gameMap.row_counts[gameMap.playerTag][i]
	# Footer row
	for j in range(len(gameMap.contents[0])):
		s = "%s\t%d" % (s,gameMap.col_counts[gameMap.playerTag][j])
	s = "%s\n" % s
	return s
	
class Move:
	def __init__(self, loc=0, direction=0):
		self.loc = loc
		self.directionSites = {}
		self.addDirection(direction)
		
	def addDirection(self, direction):
		if type(direction) == list:
			for d in direction:
				self.directionSites[d] = None
		else:
			self.directionSites[direction] = None
	
	def getDirections(self):
		return list(self.directionSites.keys())
	
	def setDirections(self, directions):
		self.directionSites = {}
		self.addDirection(directions)
		
	def getDirectionSites(self):
		self.lazyLoadSites()
		return self.directionSites.items()
	
	def getSites(self):
		self.lazyLoadSites()
		return list(self.directionSites.values())
		
	def lazyLoadSites(self):
		for d in self.directionSites.keys():
			if not self.directionSites[d]:
				self.directionSites[d] = gameMap.getSite(self.loc, d)
	
	def __str__(self):
		return "%s->%s" % (self.loc,self.getDirections())
		
	def __lt__(self, other):
		return self.loc < other.loc
	
class MoveFork:
	def __init__(self, myMap, initial_moves):
		self.gameMap = weakref.ref(myMap)
		self.move_list = []
		self.unused_moves = set(initial_moves)
		self.used_moves = set()
		self.spread_gap = []
	
	def check_escapes(self, site):
		best_total = site.projected_str
		best_move = Move(site.loc, STILL)
		shuffle(CARDINALS)
		moves_logger.debug("Route: %s(%s)" % (best_move,best_total) )
		for dir in CARDINALS:
			fsite = gameMap.getSite(site.loc, dir)
			if fsite.owner == gameMap.playerTag:
				total = fsite.projected_str + site.strength
			else:
				total = fsite.projected_str - site.strength
			# use the escape if it's us and better or use a neutral only if the best so far involves waste
			#if ( fsite.owner == gameMap.playerTag and (total > best_total and total < 255) or best_move.getSites()[0].owner != gameMap.playerTag) or best_total > 255:
			
			if ( fsite.owner == gameMap.playerTag and total > best_total and total < balance.strength_limit(fsite)):
				best_total = total
				best_move = Move(site.loc, dir)
			#elif (best_move.getSites()[0].owner != gameMap.playerTag and best_total > balance.strength_limit(fsite) and total < best_total):
			#	best_total = total
			#	best_move = Move(site.loc, dir)
			moves_logger.debug("Route: %s(%s)" % (Move(site.loc, dir),total) )
		moves_logger.debug("Escape: %s(%s)" % (best_move,best_total) )
		return best_move, best_total
	
	def find_escapes(self, sites):
		best_move = None
		best_total = 100000000
		for site in sites:
			site_move, site_total = self.check_escapes(site)
			if site_total < best_total:
				best_move = site_move
				best_total = site_total
	
		moves_logger.debug("Best Escape: %s(%s)" % (best_move,best_total) )
		return best_move
	
	def getMoveStrength(self, move):
		return gameMap.getDistance(move.loc.gameMap().getTerritory(gameMap.playerTag).getCenter())
	
	def approve_move(self, move, approved):
		approved.append(move)
		site = gameMap.getSite(move.loc)
		target = gameMap.getSite(site.loc,move.getDirections()[0])
		moves_logger.debug("Old pstr: src=%s%s | tar=%s%s" % (move.loc.site().projected_str,target.loc,target.projected_str) )
		site.projected_str -= site.strength
		if target.owner == gameMap.playerTag:
			target.projected_str += site.strength
		else:
			target.projected_str -= site.strength
			if target.projected_str < 0:
				target.owner = gameMap.playerTag
				target.projected_str *= -1
		moves_logger.debug("New pstr: src=%s%s | tar=%s%s" % (move.loc.site().projected_str,target.loc,target.projected_str) )
	
	def passesMoveFilter(self, site, direction, target):
		
		
		if not (target.owner != gameMap.playerTag or ((direction != 0 and ((target.projected_str + site.strength) <= balance.strength_limit(target))) or (direction == 0 and (target.projected_str <= balance.strength_limit(target))))):
			return False
			

		
		return True

	
	
	# instead of resolving moves in the order they came in, invert the list to get the set of moves that are moving to a tile and resolve those from the outside in
	# biggest tiles 
	def resolve_moves_iteratively(self, pending):
		approved = []
		continuing = True
		
		queue = copy.copy(pending) #sorted(pending, key=self.getMoveStrength, reverse=True)
		
		while len(queue) > 0:
			while continuing:
				continuing = False
				new_queue = []
				# moves_logger.debug("Resolving %s moves" % len(queue) )
				for move in queue:
					move_approved = False
					
					site = gameMap.getSite(move.loc)
					for direction, target in move.getDirectionSites():
						moves_logger.debug("target.owner != gameMap.playerTag or ((direction != 0 and ((target.projected_str + site.strength) <= 255)) or (direction == 0 and (target.projected_str <= 255)))" )
						moves_logger.debug("      %s                  %s                 %s                    %s                   %s" %(target.owner,gameMap.playerTag,direction,target.projected_str,site.strength) )
						moves_logger.debug("            %s                             %s                                          %s                          %s                          %s" %(target.owner != gameMap.playerTag,direction != 0, target.projected_str + site.strength <= balance.strength_limit(target), direction == 0, target.projected_str <= balance.strength_limit(target)) )
						
						if self.passesMoveFilter(site, direction, target):
							continuing = True
							move_approved = True
							move.setDirections([direction])
							self.approve_move(move, approved)
							break
							moves_logger.debug("New pstr: src=%s%s | tar=%s%s" % (move.loc.site().projected_str,target.loc,target.projected_str) )
							
					if not move_approved:
						new_queue.append(move)
				queue = new_queue
			
			# for performance, we might revert to not checking the queue after each fix
			if queue:
				moves_logger.debug("Unable to resolve queue: %s" % debug_list(new_queue))
				#move = queue[0]
				#queue = queue[1:]
				for move in queue:
					moves_logger.debug("Escaping %s" % move)
					site = gameMap.getSite(move.loc)
					sites = [site]
					for dir, target in move.getDirectionSites():
						if dir != 0:
							sites.append(target)
					esc_move = self.find_escapes(sites)
					self.approve_move(esc_move, approved)
					continuing = True
				queue = []
		return approved
	

		
	def output_all_moves(self):
		from networking2 import sendString
		#self.move_list = self.resolve_moves_iteratively(pending=self.move_list)
		
		returnString = ""
		for move in self.move_list:
			site = self.gameMap().getSite(move.loc)
			dir = move.getDirections()[0]
			# target_site = gameMap.getSite(move.loc, dir)
			# moves_logger.debug("Sending: %s (str: %s) dir %s to %s (pstr: %s)" % (move.loc, site.strength, move.getDirections(), target_site.loc, target_site.projected_str) )
			if dir != 0:
				returnString += str(move.loc.x) + " " + str(move.loc.y) + " " + str(move.getDirections()[0]) + " "
		sendString(returnString)
	
	def submit_moves(self, moves, weak=False):
		for move in moves:
			self.submit_move(move, weak)
	
	def submit_move(self, move, weak=False):
		loc = move.loc
		site = loc.site()
		# if site.strength == 0:
			# move = Move(loc, 0)
		
		# This will raise an exception if you try to use a move twice because you won't be able to remove it
		
		# moves_logger.debug("Submitting move (weak=%s): %s" % (weak,move))

		if weak and not loc in self.unused_moves:
			return
		
		self.unused_moves.remove(loc)
		self.used_moves.add(loc)
		self.move_list.append(move)
		
	def get_unused_moves(self):
		return self.unused_moves
	
	def get_used_moves(self):
		return self.used_moves
		
	def fork(self, root_fork):
		mf = MoveFork()
		mf.move_list = copy.copy(self.move_list)
		mf.unused_moves = copy.copy(self.unused_moves)
		mf.used_moves = copy.copy(self.used_moves)
		return mf