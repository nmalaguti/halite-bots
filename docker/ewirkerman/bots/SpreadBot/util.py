def debug_list(list):
	s = ",".join([str(e) for e in list])
	return "[%s]" % s
	
def getOppositeDir(dir):
	if dir == 0:
		return 0
	return (((dir - 1) + 2) % 4) + 1
	
	