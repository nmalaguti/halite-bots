import random
import math
import copy

import logging

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
		return hash(','.join(["%i"% self.x,"%i"% self.y]))
		#ikey = ""
		#for c in key:
		#	ikey += str(ord(c))
		#return int(ikey)

class Site:
	def __init__(self, owner=0, strength=0, production=0, friends = [], neutrals = 4, enemies = [], loc = None):
		self.owner = owner
		self.strength = strength
		self.production = production
		self.friends = []
		self.neutrals = neutrals
		self.enemies = []
		self.loc = loc
	
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
	
		
class Move:
	def __init__(self, loc=0, direction=0):
		self.loc = loc
		self.direction = direction
	
	def __str__(self):
		return "%s->%s" % (self.loc,self.direction)

class Territory:
	def __init__(self, owner, map):
		self.count = 0
		self.owner = owner
		self.territory = set()
		self.frontier = set()
		self.fringe = set()
		self.map = map
		self.center = None
		self.fullrow = None
		self.fullcol = None
	
	def addFrontier(self, location):
		self.frontier.add(location)
		
	def addFringe(self, location):
		self.fringe.add(location)
	
	def addLocation(self, location):
		self.count += 1
		self.territory.add(location)

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

		for y in range(0, self.height):
			row = []
			for x in range(0, self.width):
				# logger.debug("Creating site and Loc for %s, %s" % (x,y))
				row.append(Site(0, 0, 0, loc = self.getLocationXY(x,y)))
			self.contents.append(row)
	
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
	
	def getLocationXY(self, x, y, direction = STILL):
		loc_key = ','.join(["%i"% x,"%i"% y])
		#logger.debug(str(len(location_cache)) + " " + loc_key)
		if not location_cache.get(loc_key):
			location_cache[loc_key] = {}
		if not location_cache[loc_key].get(direction):
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
		return location_cache[loc_key][direction]
		
		
	def getSite(self, l, direction = STILL):
		#logger.debug("getSite")
		l = self.getLocation(l, direction)
		#logger.debug("getSite found location %s" % l)
		return self.contents[l.y][l.x]
	
	def getTerritory(self, owner):
		if not self.territories.get(owner):
			self.territories[owner] = Territory(owner, self)
		return self.territories[owner]

	def clearTerritories(self):
		self.territories = {}
		
	def getTerritories(self):
		return self.territories.values()
		
	def defineTerritories(self):
		for t in self.getTerritories():
			for loc in t.territory:
				frontier = False
				for dir in CARDINALS:
					new_loc = self.getLocation(loc, dir)
					new_site = self.getSite(new_loc)
					if new_site.owner == 0:
						frontier = True
						#logger.debug("New fringe: %s" % new_loc)
						t.addFringe(new_loc)
						#map_logger.debug("Fringe:")
						#for f in t.fringe:
						#	logger.debug(f)
					#map_logger.debug("Updating %s... %s" % (str(new_loc), new_site) )
				if frontier:
					t.addFrontier(loc)
					#logger.debug("New frontier: %s" % loc)
		
	def updateMap(self, move):
		s = self.getSite(move.loc).strength 
		self.getSite(move.loc).strength = 0
		self.getSite(move.loc, move.getDirections()[0]).strength += s
		
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
	
