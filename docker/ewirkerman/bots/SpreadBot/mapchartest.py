from hlt2 import *
from moves import *

loc = Location(0,0)

m1 = Move(loc,1)
m2 = Move(loc,2)
m3 = Move(loc,3)
m4 = Move(loc,4)

d = {}


cases = [m1, m2, m3, m4, [m1,m2], [m1,m3], [m1,m4], [m2,m3], [m2,m4],[m3,m4], [m1,m2,m3],[m1,m2,m4],[m1,m3,m4],[m2,m3,m4],[m1,m2,m3,m4] ]

for case in cases:
	setMapChar(d, case)
	print(d.values())
	
