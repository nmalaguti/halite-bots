import pstats
import os

for f in os.listdir("stats"):
	p = pstats.Stats('stats\%s' % f)
	p.sort_stats("cumtime").print_stats()
