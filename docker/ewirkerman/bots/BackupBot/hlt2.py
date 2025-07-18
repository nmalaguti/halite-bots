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

class Location:
	def __init__(self, x=0, y=0):
		self.x = x
		self.y = y
	
	def getRealCenter(self):
		return (self.x,self.y)
	
	def __str__(self):
		return str((self.x,self.y))
		
	def __eq__(self, loc):
		return self.x == loc.x and self.y == loc.y
		
	def __hash__(self):
		return int(1/2*(self.x + self.y)*(self.x + self.y + 1) + self.y)
		#ikey = ""
		#for c in key:
		#	ikey += str(ord(c))
		#return int(ikey)
		
	def __lt__(self, other):
		return self.__hash__() < other.__hash__()

class Site:
	def __init__(self, owner=0, strength=0, production=0, friends = [], neutrals = 4, enemies = [], loc = None):
		self.owner = owner
		self.strength = strength
		self.production = production
		self.friends = []
		self.neutrals = []
		self.enemies = []
		self.loc = loc
		loc.site = self
		self.enemy_str = 0
	
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
		
maxima_sets = []
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
		self.maxima_sets = maxima_sets

		logger.info("Recreating all the sites")
		for y in range(0, self.height):
			row = []
			for x in range(0, self.width):
				# logger.debug("Creating site and Loc for %s, %s" % (x,y))
				row.append(Site(0, 0, 0, loc = self.getLocationXY(x,y)))
			self.contents.append(row)
		logger.info("Recreated all the sites")
	
	def inBounds(self, l):
		return l.x >= 0 and l.x < self.width and l.y >= 0 and l.y < self.height
		
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
		loc_key = 1/2*(x + y)*(x + y + 1) + y
		#logger.debug(str(len(location_cache)) + " " + loc_key)
		if not location_cache.get(loc_key):
			location_cache[loc_key] = {}
		if not location_cache[loc_key].get(direction):
			self.updateLocationDirection(x,y,loc_key,direction)
			
		return location_cache[loc_key][direction]
		
		
	def getSite(self, l, direction = STILL):
		#logger.debug("getSite")
		l = self.getLocation(l, direction)
		#logger.debug("getSite found location %s" % l)
		return self.contents[l.y][l.x]
	
	def getTerritory(self, owner=None):
		if not owner:
			owner = self.playerTag
		if not self.territories.get(owner):
			self.territories[owner] = Territory(owner, self)
		return self.territories[owner]

	def clearTerritories(self):
		self.territories = {}
		
	def getTerritories(self):
		return self.territories.values()

	def getProdMap(self, production_sets):
		s = "\n\t"
		
		# Header row
		for j in range(len(self.contents[0])):
			s = "%s\t%d" % (s,j)
		s = "%s\n" % s
		
		for i in range(len(self.contents)):
			# Header column
			row = self.contents[i]
			s = "%s%d\t" % (s,i)
			
			for j in range(len(row)):
			
				#### This sets the display value of the map
				l = self.getLocationXY(j,i)
				column = ""
				if any([l in set for set in production_sets]):
					column = "%s" % l.site.production
				
				####
				s = s + "\t" + str(column)
				
			s = s + "\n"
		
		s = "%s\n" % s
		return s
	
	def findLocalMaxima(self, production_sets):
		global maxima_sets
	
		# convert the tuples to sets so we can act on them
		all_remaining = set()
		for i in range(0,20):
			if i in production_sets:
				p_set = set()
				for tup in production_sets[i]:
					p_set.add(self.getLocationXY(tup[0],tup[1]))
				production_sets[i] = p_set
				all_remaining.update(p_set)
	
		logger.debug(self.getProdMap(production_sets.values()))
		
		checked_set = set()
		maxima_sets = []
		for location in copy.copy(all_remaining):
			#logger.debug("Starting set from %s" % location)
			if location in all_remaining:
				maxima_set = {location}
				spread_set = {location}
				abort = False
				
				while len(spread_set) > 0:
					loc = spread_set.pop()
					site_prod = loc.site.production
					#logger.debug("Looking at %s with production %s" % (loc, site_prod))
					#logger.debug("Remaining in spread_set: %s" % len(spread_set) )
					all_remaining.remove(loc)
					for direction in CARDINALS:
						##logger.debug("Checking CARDINAL %s" % (direction))
						d_loc = self.getLocation(loc, direction)
						d_site_prod = d_loc.site.production
						if d_site_prod > site_prod:
							abort = True
							#logger.debug("Checking against %s with production %s - Aborting" % (d_loc, d_site_prod))
						elif d_site_prod == site_prod:
							#logger.debug("Checking against %s with production %s - Spreading" % (d_loc, d_site_prod))
							if d_loc in all_remaining:
								maxima_set.add(d_loc)
								spread_set.add(d_loc)
						else:
							#logger.debug("Checking against %s with production %s - Lower" % (d_loc, d_site_prod))
							pass
				if not abort:
					#logger.debug("Maxima_set: %s" % debug_list(maxima_set))
					maxima_sets.append(maxima_set)
				else:
					#logger.debug("Aborted set: %s" % debug_list(maxima_set))
					pass
		
		logger.debug(self.getProdMap(maxima_sets))


		
		
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
				#logger.debug("Territory Loc: %s" % loc)
				site = loc.site
				t.strength += site.strength
				#logger.debug("Friends: %s" % debug_list(site.friends))
				#logger.debug("Neutrals: %s" % debug_list(site.neutrals))
				#logger.debug("Enemies: %s" % debug_list(site.enemies))
				fdirs = [ getOppositeDir(d) for move in site.friends for d in move.getDirections()]
				#logger.debug("fdirs: %s" % fdirs)
				for dir in [d for d in CARDINALS if not d in fdirs]:
					new_loc = self.getLocation(loc, dir)
					#logger.debug("Neutral: %s->%s" % (new_loc,dir))
					t.addFringe(new_loc)
					self.setupFringeLoc(new_loc)
					t.addFrontier(loc)
	
	def	getDefaultChar(self, loc):
		t = self.getTerritory(self.playerTag)
		if l in t.fringe:
			column = "___"
		elif l in t.frontier:
			column = "..."
		return column
		
	def mapToStr(self, center, char_func=getDefaultChar):
		s = "\n"
		
		
		# Header row
		for j in range(len(self.contents[0])):
			s = "%s\t%d" % (s,j)
		s = "%s\n" % s
		
		for i in range(len(self.contents)):
			# Header column
			row = self.contents[i]
			s = "%s%d" % (s,i)
			
			for j in range(len(row)):
				#### This sets the display value of the map
				l = self.getLocationXY(j,i)
				column = self.getDefaultChar(l) or row[j]
				
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
	