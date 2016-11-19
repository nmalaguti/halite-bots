from subprocess import call
from glob import glob
import os
import sys

os.chdir(os.path.dirname(os.path.realpath(__file__)))

nodebins = glob('node-*/bin/node')
if len(nodebins) == 0:
    nodebin = 'node'
else:
    nodebin = nodebins[0]

call([nodebin, 'JavaScriptBot.js'], stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr)
