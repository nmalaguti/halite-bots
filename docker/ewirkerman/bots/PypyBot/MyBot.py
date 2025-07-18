from subprocess import call
from glob import glob
import os

# cd to the script directory
os.chdir(os.path.dirname(os.path.realpath(__file__)))

# look for the pypy executable
pypybin = glob('pypy*/bin/pypy')
if len(pypybin) == 0:
    # if it isn't found we're probably running locally
    pypybin = 'python'
else:
    pypybin = pypybin[0]

path = pypybin + " ./pypyBot.py"
if os.name == 'nt':
	pypybin = "C:\Progra~2\pypy\pypy.exe"
	
	path = pypybin + " ./pypyBot.py"
	# path = pypybin + " ./bots/MyBot/pypyBot.py"


call(path, shell=True)
