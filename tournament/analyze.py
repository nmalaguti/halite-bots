from json import load
from pprint import pprint
from collections import namedtuple
from itertools import chain, zip_longest
from statistics import mean, pstdev
from math import ceil
from operator import itemgetter

def grouper(iterable, n, fillvalue=None):
    "Collect data into fixed-length chunks or blocks"
    # grouper('ABCDEFG', 3, 'x') --> ABC DEF Gxx"
    args = [iter(iterable)] * n
    return zip_longest(*args, fillvalue=fillvalue)

Square = namedtuple('Square', 'x y owner strength production')
Point = namedtuple('Point', 'x y')

replay = load(open('replay.hlt'))

width = replay['width']
height = replay['height']
productions = replay['productions']
first_frame = replay['frames'][0]

grid = [[Square(x, y, owner, strength, production)
         for x, ((owner, strength), production)
         in enumerate(zip(owner_strength_row, productions_row))]
        for y, (owner_strength_row, productions_row)
        in enumerate(zip(first_frame, productions))]

def resource(square):
    if square.production == 0:
        return square.strength
    return square.strength / square.production

def distance(sq1, sq2):
    "Returns Manhattan distance between two squares."
    dx = min(abs(sq1.x - sq2.x), sq1.x + width - sq2.x, sq2.x + width - sq1.x)
    dy = min(abs(sq1.y - sq2.y), sq1.y + height - sq2.y, sq2.y + height - sq1.y)
    return dx + dy

window_size = 8

windows = []

double_grid = grid + grid

print(width, height)

for y in range(0, height):
    for x in range(0, width):
        windows.append(([Point(x, y), Point(x + window_size, y), Point(x, y + window_size), Point(x + window_size, y + window_size)], mean(map(resource, chain(*map(lambda l: (l+l)[x:x+window_size], double_grid[y:y+window_size]))))))

mycell = list(filter(lambda s: s.owner == 1, chain(*grid)))[0]
pprint(mycell)

# pprint(windows)

data_mean = mean(map(itemgetter(1), windows))
data_stddev = pstdev(map(itemgetter(1), windows), data_mean)


print('window_size', window_size)
print('mean', data_mean)
print('stddev', data_stddev)
print('2 stddev', data_mean - data_stddev * 1.9)

def below_two_stddev(entry):
    return entry[1] < 3

pprint(sorted(list(zip(map(lambda s: distance(mycell, s), chain(*map(itemgetter(0), filter(below_two_stddev, windows)))), chain(*map(lambda points: zip(points[0], [points[1]] * len(points[0])), filter(below_two_stddev, windows)))))))



