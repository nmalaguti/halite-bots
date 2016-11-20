from subprocess import call
import os
import sys

os.chdir(os.path.dirname(os.path.realpath(__file__)))

call(['./bin/MyBot'], stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr)
