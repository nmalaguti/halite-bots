from hlt2 import *
from moves import Move
import socket
import traceback
from ctypes import *
import sys
import logging
import traceback
from random import shuffle

#map_logger = logging.getLogger('bot')
try:
#	if len(map_logger.handlers) < 1:
		base_formatter = logging.Formatter("%(asctime)s : %(levelname)s %(message)s")
		log_file_name = 'bot.debug'
		hdlr = logging.FileHandler(log_file_name)
		hdlr.setFormatter(base_formatter)
		hdlr.setLevel(logging.DEBUG)
#		#map_logger.addHandler(hdlr)
#		#map_logger.setLevel(logging.DEBUG)	
except NameError:
	pass

test_inputs = [
"1 0 1 2 1 1 1 0 108 176 176 108 ",
"2 0 1 1 1 2 236 236 80 80 ",
"14 14 5 5",
"2 2",
"1"
]

rng_CARDINALS = copy.copy(CARDINALS)
_productions = []
_width = -1
_height = -1
playerTag = -1

def serializeMoveSet(moves):
	returnString = ""
	for move in moves:
		returnString += str(move.loc.x) + " " + str(move.loc.y) + " " + str(move.getDirections()) + " "
	return returnString

def deserializeMapSize(inputString):
	splitString = inputString.split(" ")

	global _width, _height
	_width = int(splitString.pop(0))
	_height = int(splitString.pop(0))

production_min_set = []
def deserializeProductions(inputString):
	global production_min_set
	splitString = inputString.split(" ")
	
	min = 100

	for a in range(0, _height):
		row = []
		for b in range(0, _width):
			prod = int(splitString.pop(0))
			if prod <= min:
				if prod < min:
					production_min_set = []
				production_min_set.append((b,a))
			row.append(prod)
		_productions.append(row)
		
def mark_neighbors(map, loc, type):
	# shuffle(rng_CARDINALS)
	# myCards = copy.copy(rng_CARDINALS)
	for dir in CARDINALS:
		new_loc = map.getLocation(loc, dir)
		new_site = new_loc.site
		getattr(new_site, type).append(Move(loc, dir))
		type_list = getattr(new_site, type)
		
#		#map_logger.debug("New %s list for %s: %s" % (type, new_loc,debug_list(type_list)) )
		
		# we can't mark the neutrals around a friend at this point because we haven't set all the owner
		#if type == "friends" and new_site.owner == 0:
#		#	#map_logger.debug("Incrementing a fringe neutral at %s" % new_loc)
		#	increment_neighbors(map, new_loc, new_site.owner)

def increment_neighbors(map, loc, owner):
	curr_site = map.getSite(loc)
	if owner != 0:
#		# logger.debug("Found location %s with owner %s (should be %s and %s)" % (loc.site.loc, loc.site.owner, loc, owner))
		pass
	if owner > 0:
		t = map.getTerritory(owner)
		t.addLocation(curr_site)
	
	if owner == playerTag:
		type = "friends"
	elif owner == 0: 
		type = "neutrals"
		if curr_site.strength == 0:
			type = "empties"
			mark_neighbors(map, loc, type)
	else:
		type = "enemies"
#	# map_logger.debug("Increment_neighbors neighbors of %s with type %s" % (loc, type))
	mark_neighbors(map, loc, type)

def deserializeMap(m, inputString):
#	logger.debug("Deserializing board")
	splitString = inputString.split(" ")


	y = 0
	x = 0
	counter = 0
	owner = 0
	for a in range(0, _height):
		for b in range(0, _width):
			m.contents[a][b].production = _productions[a][b]
	
	while y != m.height:
		counter = int(splitString.pop(0))
		owner = int(splitString.pop(0))
		
		for a in range(0, counter):
#			#map_logger.debug("%s,%s" % (y,x))
			if owner != 0:
#				# logger.debug("Found location %s, %s with owner %s" % (x, y, owner))
				pass
			m.contents[y][x].owner = owner
			if owner != 0:
#				# logger.debug("Set site for %s, %s to owner %s" % (x, y, m.contents[y][x].owner))
				pass
#			#map_logger.debug("Retrieving Loc")
			loc = m.getLocationXY(x,y)
			if owner != 0:
#				# logger.debug("Retrieved location %s with owner %s (should be %s and %s)\n" % (loc.site.loc, loc.site.owner, loc, owner))
				pass
			loc.gameMap = m
			if owner > 0:
				increment_neighbors(m, loc, owner)
				m.updateCounts(owner, loc)
			if owner != 0:
#				# logger.debug("Found location %s with owner %s (should be %s and %s)\n" % (loc.site.loc, loc.site.owner, loc, owner))
				pass
			x += 1
			if x == m.width:
				x = 0
				y += 1
		
	for a in range(0, _height):
		for b in range(0, _width):
			m.contents[a][b].strength = int(splitString.pop(0))
			m.contents[a][b].projected_str = m.contents[a][b].strength
			# m.contents[a][b].production = _productions[a][b]

#	logger.debug("Deserializing complete")
	return m

def sendString(toBeSent):
	toBeSent += '\n'

	sys.stdout.write(toBeSent)
	sys.stdout.flush()

def getStringTest():
	s = test_inputs.pop()
#	##map_logger.debug("Got String(): %s" % s)
	return s
	
def getString():
	line = sys.stdin.readline().rstrip('\n')
#	map_logger.debug("Game State: %s" % line)
	return line

	
local_maxima = None
def getInit(getString=getString):
	global playerTag
	playerTag = int(getString())
	deserializeMapSize(getString())
	deserializeProductions(getString())
#	#map_logger.debug("Finished Map init")
	m = GameMap(_width, _height, playerTag = playerTag)
	
#	#map_logger.debug("Caching map relations")
	for y in range(m.height):
		for x in range(m.width):
			l = Location(x,y)
			for dir in CARDINALS:
				m.getLocation(l, dir)
	
	deserializeMap(m, getString())
	
	global local_maxima
	local_maxima = m.findLocalMaxima(production_min_set)
	
	return (playerTag, m)

def sendInit(name):
	sendString(name)

def getFrame(test_string = None):
	m = GameMap(_width, _height, playerTag = playerTag)
	if test_string:
		deserializeMap(m, test_string)
	else:
		deserializeMap(m, getString())
	m.defineTerritories()
	m.local_maxima = local_maxima
	return m

def sendFrame(moves):
	sendString(serializeMoveSet(moves))
	
def testBot():
	myID, gameMap = getInit(getStringTest)
	global _productions
	global _width
	global _height
	global playerTag
	_productions = []
	_width = -1
	_height = -1	
	playerTag = -1
	
