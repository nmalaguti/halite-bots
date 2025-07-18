import random
import math
import copy
import logging
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
		self.hash = None
	
	def getRealCenter(self):
		return (self.x,self.y)
	
	def __str__(self):
		return str((self.x,self.y))
		
	def __eq__(self, loc):
		return loc.hash == self.hash
		
	def __hash__(self):
		# return hash(','.join(["%i"% self.x,"%i"% self.y]))
		if not self.hash:
			self.hash = self.y * self.gameMap.width + self.x
		return self.hash
		#ikey = ""
		#for c in key:
		#	ikey += str(ord(c))
		#return int(ikey)
	
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
		loc.site = self
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

class Territory:
	def __init__(self, owner, map):
		self.count = 0
		self.owner = owner
		self.territory = set()
		self.frontier = set()
		self.fringe = set()
		self.spread_zone = set()
		self.map = map
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
	
	def addLocation(self, location):
		self.count += 1
		if not type(location) is Site:
			site = self.map.getSite(location)
		else:
			site = location
			location = site.loc
		self.territory.add(location)
		self.production += site.production
#		# logger.debug("Adding %s production from %s to %s's territory" % (site.production, location, self.owner) )
		self.strength += site.strength

	def getLocations(self):
		return self.territory

	def getCenter(self):
		if not self.center:
			center_y = self.map.row_counts[self.owner].index(max(self.map.row_counts[self.owner]))
			center_x = self.map.col_counts[self.owner].index(max(self.map.col_counts[self.owner]))
			self.center = self.map.getLocationXY(center_x, center_y)
		return self.center
		
	def getFrontier(self):
		return self.frontier
		
	def isCenterRowFull(self):
		if self.fullrow == None:
			#y = self.getCenter().y
			self.fullcol = any( [all([l.owner == self.owner for l in self.map.getRow(y)]) for y in range(self.map.height)])
		return self.fullrow
		
	def isCenterColumnFull(self):
		if self.fullrow == None:
			#x = self.getCenter().x
			self.fullcol = any( [all([l.owner == self.owner for l in self.map.getColumn(x)]) for x in range(self.map.width)])
		return self.fullcol		
		
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

		logger.info("Recreating all the sites")
		for y in range(0, self.height):
			row = []
			for x in range(0, self.width):
#				# logger.debug("Creating site and Loc for %s, %s" % (x,y))
				row.append(Site(0, 0, 0, loc = self.getLocationXY(x,y)))
			self.contents.append(row)
		logger.info("Recreated all the sites")
	
	def inBounds(self, l):
		return l.x >= 0 and l.x < self.width and l.y >= 0 and l.y < self.height
		
	def num_non_friends(self, loc):
#		# logger.debug("Found %s non_friends" % (len(loc.site.empties) + len(loc.site.enemies)) )
		return len(loc.site.empties) + len(loc.site.enemies)
	
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
		return self.getLocationXY(loc.x, loc.y, direction)
		
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
		return [loc.site for loc in neighbor_range_cache[loc][n]]

		
	def updateLocationDirection(self, x,y,loc_key,direction):
		if direction != STILL:
			if direction == NORTH:
				if y == 0:
					y = self.height - 1
				else:
					y -= 1
			elif direction == EAST:
				if x == self.width - 1:
					x = 0
				else:
					x += 1
			elif direction == SOUTH:
				if y == self.height - 1:
					y = 0
				else:
					y += 1
			elif direction == WEST:
				if x == 0:
					x = self.width - 1
				else:
					x -= 1
			location_cache[loc_key][direction] = self.getLocationXY(x,y)
		else:
			location_cache[loc_key][STILL] = Location(x,y)
	
	def getLocationXY(self, x, y, direction = STILL):
		loc_key = y*self.width + x
		if loc_key not in location_cache:
			location_cache[loc_key] = {}
		if direction not in location_cache[loc_key]:
			self.updateLocationDirection(x,y,loc_key,direction)
		
		return location_cache[loc_key][direction]
		
		
	def getSite(self, l, direction = STILL):
#		#logger.debug("getSite")
		if not direction:
			return l.site
		l = self.getLocation(l, direction)
		return l.site
	
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
	
	def get_enemy_strength(self, loc, range=None, damage_dealable=False, breach=False):
	
		enemy_str = 0
		max_damage = 256
		if damage_dealable:
			range = range or 1
			max_damage = loc.site.strength
		if not range:
			if damage_dealable:
				range = 1
			else:
				range = 2
		neighbors = self.neighbors(loc, n=range-1, include_self=True)
		for site in neighbors:
			if site.owner or not site.strength or breach:
				enemy_str += sum([min([move.loc.site.strength, max_damage]) for move in site.enemies])
#		# logger.debug("Got enemy strength for %s and found %s sites within %s range with %s strength" % (loc, len(neighbors), range, enemy_str))
		return enemy_str	
	
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
	#			if all([spread_loc.site.production <= loc.site.production for spread_loc in spread_locs]):
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

							if neighbor.site.production > loc.site.production:
								if not spoiled:
#									# logger.debug("Set was spoiled by higher proudction at: %s" % neighbor)
									spoiled = True
							elif neighbor in used_tiles or neighbor in this_set:
								continue
							elif neighbor.site.production == loc.site.production:
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
		site = loc.site
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
				site = loc.site
#				#logger.debug("Friends: %s" % debug_list(site.friends))
#				#logger.debug("Neutrals: %s" % debug_list(site.neutrals))
#				#logger.debug("Enemies: %s" % debug_list(site.enemies))
				if t.owner == t.map.playerTag:
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
	
