import random
import math
import copy
import logging
import heapq
import weakref
from util import *


logger = logging.getLogger("bot")

STILL = 0
NORTH = 1
EAST = 2
SOUTH = 3
WEST = 4

DIRECTIONS = [a for a in range(0, 5)]
CARDINALS = [a for a in range(1, 5)]

ATTACK = 0
import hashlib
STOP_ATTACK = 1

location_cache = {}
neighbor_range_cache = {}

class Location:
	def __init__(self, x=0, y=0):
		self.x = x
		self.y = y
		self.site = None
		self.N = None
		self.E = None
		self.S = None
		self.W = None
		self.hash = None
		self.shareable_moves = {}
		self.trails = []
		self.expanding_trails = []
	
	def getRealCenter(self):
		return (self.x,self.y)
	
	def __str__(self):
		return str((self.x,self.y))
		
	def __eq__(self, loc):
		return loc.hash == self.hash
		
	def __hash__(self):
		# return hash(','.join(["%i"% self.x,"%i"% self.y]))
		if not self.hash:
			self.hash = self.y * self.gameMap().width + self.x
		return self.hash
		#ikey = ""
		#for c in key:
		#	ikey += str(ord(c))
		#return int(ikey)
		
	def get_best_trail(self):
		last_best = self.trails[0]
	
#		# logger.debug("\nList of trails for %s:" % self)
		# l = list(self.trails)
		# while l:
			# loc = heapq.heappop(l)
#			# logger.debug("%s" % loc)
			# pass
		
		
#		logger.debug("%s"%[len(loc.site().neutrals) for loc in last_best.path])
		while (len(last_best.path[0].site().neutrals) < 3 or any([len(loc.site().enemies) > 0 for loc in last_best.path[1:]])) and len(self.trails) > 1:
#			logger.debug("Eliminating %s" % last_best)
			heapq.heappop(self.trails)
			last_best = self.trails[0]
		
		if not self.trails:
#			# logger.debug("Returning %s, %s remaining" % (None, len(self.trails)))
			return None
#		# logger.debug("Returning %s, %s remaining" % (last_best, len(self.trails)))
		return last_best
		
	def peer_deeper(self):
		# depth = min([self.gameMap().getTerritory().production ** .33, self.gameMap().trail_search_distance_max])
		depth = self.gameMap().trail_search_distance_max
		# depth = 10
		
		old_trails = list(self.expanding_trails)
#		# logger.debug("Expanding %s" % old_trails)
		self.expanding_trails = []
		for trail in old_trails:
			if len(trail) > 1:
				heapq.heappush(self.trails, trail)
			if len(trail) >= depth:
				continue
			children = trail.get_child_trails()
			if children:
				self.expanding_trails.extend(children)
		
	
class Site:
	def __init__(self, owner=0, strength=0, production=0, friends = [], neutrals = [], enemies = [], loc = None):
		self.owner = owner
		self.strength = strength
		self.production = production
		self.friends = []
		self.neutrals = []
		self.enemies = []
		self.empties = []
		self.loc = loc
		self.inb_str = 0
		loc.site = weakref.ref(self)
		self.enemy_str = 0
		from claim import ClaimHeap
		self.heap = ClaimHeap(self)

	
	def valOrDot(self, value, dotValue):
		if value == dotValue:
			return " "
		else:
			return str(value)
	
	def __str__(self):
		f = self.valOrDot(len(self.friends), 0)
		#n = self.valOrDot(self.neutrals, 4)
		e = self.valOrDot(self.enemies, 0)
		o = self.valOrDot(self.owner, 0)
		if self.owner != 0:
			s = str(hex(self.strength))[2:].upper().zfill(2)
		else:
			s = "  "
		return "%s" % (s)
		
class Trail:
	def __init__(self, trail=None, new_loc=None, claim=None):
		# assert claim is not None or (trail is not None and new_loc is not None)
		import balance
		self.highest_threshhold = None
		self.path = []
		self.claim = None
		
		trail_discount_base = .9
		
		if claim:
			self.claim = claim
			new_loc = claim.loc
			self.loc = new_loc
			self.site = weakref.ref(new_loc.site())
			self.benefit = balance.evalSiteProduction(self.site()) * trail_discount_base ** len(self.path)
			self.cost = balance.evalSiteStrength(self.site())
		elif trail and new_loc:
			self.claim = trail.claim
			self.loc = new_loc
			self.site = weakref.ref(new_loc.site())
			self.path.extend(trail.path)
			self.benefit = trail.benefit + balance.evalSiteProduction(self.site()) * trail_discount_base ** len(self.path)
			self.cost = trail.cost + balance.evalSiteStrength(self.site())
		elif new_loc:
			self.loc = new_loc
			self.site = weakref.ref(new_loc.site())
			self.benefit = balance.evalSiteProduction(self.site()) * trail_discount_base ** len(self.path)
			self.cost = balance.evalSiteStrength(self.site())
		
		# else:
			# raise Exception("Unable to initialize Trail with %s, %s, %s" % (claim, trail, new_loc))
		self.path.append(new_loc)
		self.gameMap = new_loc.gameMap
		self.value = self.benefit * 1.0/ self.cost
#		# logger.debug("Trail %s" % self)
		
	
	def get_child_trails(self):
		# children = []
		# for dir in CARDINALS:
			# new_loc = self.gameMap().getLocation(self.path[-1], dir)
			# if new_loc.site().owner == 0 and not new_loc in self.gameMap().getTerritory().fringe and not new_loc in self.path:
				# child = Trail(trail=self, new_loc=new_loc)
				# children.append(child)
		return [Trail(trail=self, new_loc=move.loc) for move in self.loc.site().neutrals if not move.loc in self.path]
		# return [Trail(trail=self, new_loc=move.loc) for move in self.site().neutrals if move.loc not in self.gameMap().getTerritory().fringe and not move.loc in self.path]

	def __lt__(self, other):
		return other.value < self.value
		
	def __len__(self):
		return len(self.path)
		
	def __iter__(self):
		return self.path.__iter__()
		
	def __str__(self):
		return "%s\t%s" % (self.value, debug_list(self.path))
		
	def define_threshholds(self):
		self.threshholds = []
		self.threshhold_values = []
		strength = 0
		
		for loc in self.path:
			strength += loc.site().strength
			self.threshholds.append(strength)
			
		benefit = 0
		cost = 0
#		# logger.debug("Old value is %s" % self.value)
		for i in range(len(self.path),0,-1):
			benefit += balance.evalSiteProduction(self.path[i-1].site)
			cost += balance.evalSiteStrength(self.path[i-1].site)
#			# logger.debug("last threshhold_values being set to %s" % (benefit * 1.0/ cost))
			self.threshhold_values.append(benefit  * 1.0/ cost)
#			# logger.debug("last threshhold_values now %s" % self.threshhold_values[-1])
		self.threshhold_values.reverse()
		for i in range(len(self.threshhold_values)):
#			# logger.debug("New value %s is %s" % (i,self.threshhold_values[i]))
			pass
		
	def get_value(self, strength = 0):
		if not self.gameMap().multipull:
			return self.value
	
		# logger.info("Found value %s at %s threshholds deep with strength %s" % (self.threshhold_values[i], i, strength))
		for i in range(len(self.threshholds)):
			if strength <= self.threshholds[i]:
				# logger.info("Found value %s at %s threshholds deep with strength %s" % (self.threshhold_values[i], i, strength))
				return self.threshhold_values[i]
		return self.threshhold_values[-1]
		
	def check_strength_threshhold(self, base, delta):
		# if not self.highest_threshhold:
			# self.highest_threshhold = self.find_highest_threshhold()
	
		if not self.gameMap().multipull:
			return base + delta > self.threshholds[0] or (not base and not self.threshholds[0])
	
		if not base and not self.threshholds[0]:
			return True
		
		# let's say base+delta is over the lowest threshhold so we COULD go
		# now we check how many turns it'd take to get to the next threshhold with only our total_production
		# the strength we save by doing two at once is the total_production
		# the strength we lose by waiting is the production of the lowest threshhold * the number of turns it takes to get to the next threshhold - 1
		
		# let's say we decide we want to wait for the second
		# do we wait for the third?
		# we check how many turns it will take to get to the third threshhold with our total production
		# the strength we save by doing two at once is the total_production * 2
		# the strength we lose by waiting is the production of the lowest threshhold * the number of turns it takes to get to the next threshhold -2 + the second production * turns - 1
		
		
		
		potential_multis = len(self.threshholds)
		production = self.claim.get_total_production()
		
		
		
		# the claim total production is the cost of going at a lower i * len-i
		# the production of the ith tile is the cost of waiting to a higher i * 
		
		
		first_threshhold_passed = None
		missed_production = 0
		for i in range(len(self.threshholds)):
			logger.info("Is base %s under %s and base %s + delta %s over %s?" % (base, self.threshholds[i], base, delta, self.threshholds[i]))
			
			# You definitely do to go if you don't have enough for a threshhold
			# either you're under the first one, or you would've decided to wait for a future one already
			if base + delta <= self.threshholds[i]:
				return False
			if base < self.threshholds[i] and base + delta >= self.threshholds[i]:
				if not first_threshhold_passed:
					first_threshhold_passed = i
#					logger.debug("first_threshhold_passed is %s with strength %s" % (i,self.threshholds[i]))
				
				if self.gameMap().chunkedpull:
					if i < len(self.threshholds) - 1:
						wait_turns = math.ceil((self.threshholds[i+1]-base-delta) * 1.0/ production)
						missed_production += self.path[i].site.production * (wait_turns * (i-first_threshhold_passed) - 1)
#						logger.debug("There is another threshhold beyond this one but I'd need to wait %s turns more, missing %s production" % (wait_turns, missed_production))
						move_lost_production = production*(i-first_threshhold_passed+1)
#						logger.debug("If I don't grab them together though, I'll lose %s" % (move_lost_production))
						if missed_production > move_lost_production:
							return True
					else:
						return True
				else:
					return True
		return False

class Territory:
	def __init__(self, owner, map):
		self.count = 0
		self.owner = owner
		self.territory = set()
		self.frontier = set()
		self.fringe = set()
		self.spread_zone = set()
		self.gameMap = weakref.ref(map)
		self.production = 0
		self.strength = 0
		self.center = None
		self.fullrow = None
		self.fronts = []
		self.fullcol = None
	
	def addFrontier(self, location):
		self.frontier.add(location)
		
	def addFringe(self, location):
		self.fringe.add(location)
	
	def addLocation(self, site):
		self.count += 1
		location = site.loc
		self.territory.add(location)
		self.production += site.production
		self.strength += site.strength
#		# logger.debug("Adding %s production from %s to %s's territory - now %s" % (site.production, location, self.owner, self.production) )
		

	def getLocations(self):
		return self.territory
		
	def getFrontier(self):
		return self.frontier
		
class GameMap:
	def __init__(self, width = 0, height = 0, numberOfPlayers = 0, playerTag = 0):
		self.width = width
		self.height = height
		self.contents = []
		self.territories = {}
		self.playerTag = playerTag
		self.row_counts = {}
		self.col_counts = {}
		self.living_players = set()
		self.local_maxima = []
		self.site_production_cache = {}
		# self.best_trail_cache = {}
		

		# logger.info("Recreating all the sites")
		for y in range(0, self.height):
			row = []
			for x in range(0, self.width):
#				# logger.debug("Creating site and Loc for %s, %s" % (x,y))
				l = self.getLocationXY(x,y)
				row.append(Site(0, 0, 0, loc = l))
				l.gameMap = weakref.ref(self)
			self.contents.append(row)
		# logger.info("Recreated all the sites")
	
	def inBounds(self, l):
		return l.x >= 0 and l.x < self.width and l.y >= 0 and l.y < self.height
		
	def num_non_friends(self, loc):
#		# logger.debug("Found %s non_friends" % (len(loc.site().empties) + len(loc.site().enemies)) )
		return len(loc.site().empties) + len(loc.site().enemies)
	
	def updateCounts(self, owner, loc):
		y = loc.y
		x = loc.x
		if not self.row_counts.get(owner):
			self.row_counts[owner] = [0]*self.height
		if not self.col_counts.get(owner):
			self.col_counts[owner] = [0]*self.width
		self.row_counts[owner][y] = self.row_counts[owner][y] + 1
		self.col_counts[owner][x] = self.col_counts[owner][x] + 1
	

	def getDistance(self, l1, l2):
		dx = abs(l1.x - l2.x)
		dy = abs(l1.y - l2.y)
		if dx > self.width / 2:
			dx = self.width - dx
		if dy > self.height / 2:
			dy = self.height - dy
		return dx + dy
		
	def getRow(self, y):
		return self.contents[y]
		
	def getColumn(self, x):
		return [r[x] for r in self.contents]
		
	def getAngleD(self, angle):
		angle = math.degrees(angle)
		if angle < 0:
			angle += 360
		return angle

	def getAngle(self, l1, l2):
	
		dx = l2.x - l1.x
		dy = l2.y - l1.y

		if dx > self.width - dx:
			dx -= self.width
		elif -dx > self.width + dx:
			dx += self.width

		if dy > self.height - dy:
			dy -= self.height
		elif -dy > self.height + dy:
			dy += self.height
		return math.atan2(dy, dx)
	
	def getDirection(self, l1, l2):
		deg = math.degrees(self.getAngle(l1, l2))
		if abs(deg) <= 45:
			dir = EAST
		elif abs(deg) > 135:
			dir = WEST
		elif deg > 0:
			dir = SOUTH
		elif deg < 0:
			dir = NORTH
		return dir

	def getLocation(self, loc, direction = STILL):
#		# logger.debug("Getting loc %s, with direction %s" % (loc, direction) )
		if not direction:
			return loc
		dirAttr = "HNESW"[direction]
#		# logger.debug("Checking NEW loc %s, with direction %s" % (loc, dirAttr) )
		if not getattr(loc, dirAttr):
#			# logger.debug("Getting NEW loc %s, with direction %s" % (loc, direction) )
			x = loc.x
			y = loc.y
			if direction == NORTH:
#				# logger.debug("Direction: %s"  % (direction) )
				if y == 0:
					y = self.height - 1
				else:
					y -= 1
			elif direction == EAST:
#				# logger.debug("Direction: %s"  % (direction) )
				if x == self.width - 1:
					x = 0
				else:
					x += 1
			elif direction == SOUTH:
#				# logger.debug("Direction: %s"  % (direction) )
				if y == self.height - 1:
					y = 0
				else:
					y += 1
			elif direction == WEST:
#				# logger.debug("Direction: %s"  % (direction) )
				if x == 0:
					x = self.width - 1
				else:
					x -= 1
			else:
#				# logger.debug("Shouldn't have gotten here" )
				pass
#			# logger.debug("x,y %s,%s" % (x,y) )
			new_loc = self.getLocationXY(x,y)
#			# logger.debug("Saving the %s" % new_loc )
			setattr(loc, dirAttr, new_loc )
			# oppDirAttr = "HNESW"[getOppositeDir(direction)]
			# setattr(new_loc, oppDirAttr, loc)
#		# logger.debug("Returning NEW loc %s, with direction %s" % (loc, direction) )
		return getattr(loc, dirAttr)
		
	def getLocationXY(self, x, y):
		loc_key = y*self.width+x
#		# logger.debug("Finding location for key %s" % loc_key)
		if loc_key not in location_cache:
			location_cache[loc_key] = Location(x,y)
		
#		# logger.debug("Returning location for key %s" % loc_key)
		return location_cache[loc_key]
		
	def neighbors(self, loc, n=1, include_self=False):
		#"Iterable over the n-distance neighbors of a given loc.  For single-step neighbors, the enumeration index provides the direction associated with the neighbor."
		if loc not in neighbor_range_cache:
			neighbor_range_cache[loc] = {}
		if n not in neighbor_range_cache[loc]:
#			# logger.debug("Neighbor cache miss")
			if n == 0:
				if include_self:
					neighbor_range_cache[loc][n] = [loc]
				else:
					neighbor_range_cache[loc][n] = []
			else:
				assert isinstance(include_self, bool)
				assert isinstance(n, int) and n > 0
				
				if n == 1:
					# combos = ((0, -1), (1, 0), (0, 1), (-1, 0), (0, 0))   # NORTH, EAST, SOUTH, WEST, STILL ... matches indices provided by enumerate(game_map.neighbors(square))
					neighbor_range_cache[loc][n] = include_self and [self.getLocation(loc, dir) for dir in DIRECTIONS] or [self.getLocation(loc, dir) for dir in CARDINALS]
				else:
					combos = ((dx, dy) for dy in range(-n, n+1) for dx in range(-n, n+1) if abs(dx) + abs(dy) <= n)
					neighbor_range_cache[loc][n] = [self.getLocationXY(loc.x+dx, loc.y+dy) for dx, dy in combos if include_self or dx or dy]
		return [loc.site() for loc in neighbor_range_cache[loc][n]]
		
	def getSite(self, l, direction = STILL):
#		#logger.debug("getSite")
		if not direction:
			return l.site()
		l = self.getLocation(l, direction)
		return l.site()
	
	def getTerritory(self, owner = None):
		if owner is None:
			owner = self.playerTag
		if not self.territories.get(owner):
#			# logger.debug("Creating territory for owner %s")
			self.territories[owner] = Territory(owner, self)
#		# logger.debug("Found %s territory for owner %s" % (len(self.territories[owner].territory), owner))
		return self.territories[owner]
	
	def getEnemyTerritories(self, owner = None):
		for player in range(1,6):
			if not self.territories.get(owner):
				self.territories[owner] = Territory(owner, self)
			
		ts = [t for t in self.territories.values() if t.owner != self.playerTag and t.territory]
		return ts

	def clearTerritories(self):
		self.territories = {}
		
	def closestInSets(self, set, other_set):
		class DistPair:
			def __init__(self, first, second):
				self.loc = first
				self.other_loc = second
				self.dist = getDistance(first, second)
			
			def __lt__(self, other):
				return self.dist < other.dist
				
			def __str__(self):
				return self.loc.__str__() + "->" + self.dist + "->" + self.other_loc.__str__()
				
		heap = []
		
		for loc in set:
			for other_loc in other_set:
				pair = DistPair(loc, other_loc)
#				logger.debug("Pushing %s" % pair)
				heapq.heappush(pair)
		
		return heap[0]
		
	def get_dealable_damage(self, loc):
		return self.get_friendly_strength(loc, type="enemies", damage_dealable=True)
		
	def get_friendly_strength(self, loc = None, dist=None, type="friends", damage_dealable=None):
		if not dist:
			dist = 1
		
		done = set([loc])
		strength = 0
		curr = [loc]
		max_damage = 256
		if damage_dealable:
			max_damage = loc.site().strength
		while curr and dist:
#			# logger.debug("Checking dist %s" % dist)
			dist -= 1
			next = []
			for l in curr:
				type_list = getattr(l.site(), type)
				sites = [move.loc.site() for move in type_list]
				if sites:
#					# logger.debug("Found %s sites: %s" % (type,debug_list([site.loc for site in sites])))
					pass
				for site in sites:
#					# logger.debug("checking %s, %s with str %s"%(site.owner, site.loc, site.strength))
					if site.loc not in next and site.loc not in done:
						if not site.owner and not site.strength:
							next.append(site.loc)
						elif site.owner == self.playerTag and type == "friends":
							next.append(site.loc)
							strength += site.strength + dist * site.production
						elif site.owner and site.owner != self.playerTag and type == "enemies":
							next.append(site.loc)
							if damage_dealable:
								strength += min([site.strength, max_damage])
							else:
								strength += site.strength + dist * site.production
#			# logger.debug("curr of get_friendly_strength: %s" % debug_list(curr))
			curr = next
#			# logger.debug("next of get_friendly_strength: %s" % debug_list(curr))
			done.update(curr)
#		# logger.debug("Found %s strength in %s dist of %s of type %s" % (strength, dist, loc, type))
		return strength
		
	def __exit__(self):
		pass
	def __enter__(self):
		pass
	
	def getTerritories(self):
		return self.territories.values()
		
	def findLocalMaxima(self, seed_min_set):
	#	this_wave = [self.getLocationXY(0,0)]
	#	already_waved = this_wave
	#	while len(this_wave) > 0:
	#		next_wave = []
	#		for loc in this_wave:
#	#			logger.debug("Spreading location to check: %s" % loc)
	#			spread_locs = [self.getSite(loc, d).loc for d in CARDINALS if not self.getSite(loc, d).loc in already_waved]
	#			for spread_loc in spread_locs:
	#				next_wave.append(spread_loc)
	#			if all([spread_loc.site().production <= loc.site().production for spread_loc in spread_locs]):
	#				self.local_maxima.append(loc)
	#		already_waved.extend(this_wave)
	#		this_wave = next_wave
#	#		logger.debug("Local Maxima: %s" % debug_list(this_wave))
#	#	logger.debug("Local Maxima: %s" % debug_list(self.local_maxima))
		used_tiles = set()
		for tile in [self.getLocationXY(x,y) for x in range(self.width) for y in range(self.height)]:
			if tile not in used_tiles:
				used_tiles.add(tile)
				this_set = set([tile])
				spread_set = set([tile])
				spoiled = False
				while spread_set:
					next_set = list(spread_set)
					spread_set = set()
#					# logger.debug("Spreading out from spread_set: %s" % debug_list(next_set))
					for loc in next_set:
						for neighbor in [self.getLocation(loc, d) for d in CARDINALS]:

							if neighbor.site.production > loc.site().production:
								if not spoiled:
#									# logger.debug("Set was spoiled by higher proudction at: %s" % neighbor)
									spoiled = True
							elif neighbor in used_tiles or neighbor in this_set:
								continue
							elif neighbor.site.production == loc.site().production:
#								# logger.debug("Spreading to: %s" % neighbor)
								spread_set.add(neighbor)
					this_set.update(spread_set)
						
				if not spoiled:
#					# logger.debug("Found a local maxima set: %s" % debug_list(this_set))
					self.local_maxima.append(this_set)
				else:
#					# logger.debug("Spoiled set completed: %s" % debug_list(this_set))
					pass
				used_tiles.update(this_set)
		
#		logger.debug("Maxima sets:")
		for maxima in self.local_maxima:
#			logger.debug("%s" % debug_list(maxima))
			pass
		return self.local_maxima
		
		
	def setupFringeLoc(self, loc):
		site = loc.site()
		site.local_production = site.production
		site.local_strength = site.production
		count = 1
		for dir in CARDINALS:
			adj_site = self.getSite(loc, dir)
			if adj_site.owner == 0:
				count +=1
				site.local_production += adj_site.production
				site.local_strength += adj_site.strength
		site.local_production /= count
		site.local_strength /= count

	def defineTerritories(self):
		for t in self.getTerritories():
			for loc in t.territory:
#				#logger.debug("Territory Loc: %s" % loc)
				site = loc.site()
				
				# we can't do it during the parsing because the owners come through before the strenghts
				# but this means empties don't work for neutrals because I'm not sure it's worth swinging back through all the neutrals
#				# logger.debug("Adding %s strength from %s to %s's territory - now %s" % (site.strength, location, self.owner, self.strength) )
				t.strength += site.strength
				site.empties = [move for move in site.neutrals if not move.loc.site().strength]
				
				
#				#logger.debug("Friends: %s" % debug_list(site.friends))
#				# logger.debug("Neutrals of %s: %s" % (loc, debug_list(site.neutrals)))
#				# logger.debug("Empties of %s: %s" % (loc, debug_list(site.empties)))
#				#logger.debug("Enemies: %s" % debug_list(site.enemies))
				if t.owner == t.gameMap().playerTag:
					moveset = site.friends
				else:
					moveset = site.enemies
				dirs = [ getOppositeDir(d) for move in moveset for d in move.getDirections()]
#				#logger.debug("fdirs: %s" % fdirs)
				for dir in [d for d in CARDINALS if not d in dirs]:
					new_loc = self.getLocation(loc, dir)
#					#logger.debug("Neutral: %s->%s" % (new_loc,dir))
					t.addFringe(new_loc)
					self.setupFringeLoc(new_loc)
					t.addFrontier(loc)
					
		
	def mapToStr(self, center):
		s = "\n"
		t = self.getTerritory(self.playerTag)
		
		# Header row
		for j in range(len(self.contents[0])):
			s = "%s\t%d" % (s,j)
		s = "%s\n" % s
		
		for i in range(len(self.contents)):
			# Header column
			row = self.contents[i]
			s = "%s%d" % (s,i)
			
			for j in range(len(row)):
				column = row[j]
				
				#### This sets the display value of the map
				l = self.getLocationXY(j,i)
				if l in t.fringe:
					column = "___"
				elif l in t.frontier:
					column = "..."
				
				####
				
				if center.x == j and center.y == i:
					s = s + "\t" + str("XX")
				else:
					s = s + "\t" + str(column)
			s = s + "\t%s\n" % self.row_counts[self.playerTag][i]
		# Footer row
		for j in range(len(self.contents[0])):
			s = "%s\t%d" % (s,self.col_counts[self.playerTag][j])
		s = "%s\n" % s
		return s
	
