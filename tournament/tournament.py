from trueskill import Rating, rate, rate_1vs1
from json import load, dump
from subprocess import run, PIPE
from os import listdir, remove
from random import sample, randint, choice
from glob import glob
from pprint import pprint

DB_FILENAME = 'db.json'
HALITE = './halite'
MAP_SIZES = [20, 25, 30, 35, 40, 45, 50]

db = None


def parse_result(result, numbots):
    lines = result.splitlines()
    [hlt_file, seed] = lines[numbots].split()
    ranking = [int(line.split()[1]) - 1 for line in lines[numbots + 1:numbots*2+1]]

    return hlt_file, seed, ranking


def run_halite(commands, dimension, seed=None):
    seedargs = []
    if seed:
        seedargs = ['-s', seed]
    completed_process = run([HALITE, '-q', '-d', '%s %s' % (dimension, dimension), *seedargs, *commands], stdout=PIPE, universal_newlines=True)
    hlt_file, seed, ranking = parse_result(completed_process.stdout, len(commands))
    remove(hlt_file)

    for log in glob('*.log*'):
        remove(log)

    return seed, ranking


def run_match():
    population = listdir('bots/enabled')
    bots = sample(population, randint(2, min(len(population), 6)))

    commands = ['bots/enabled/%s/run.sh' % bot for bot in bots]
    seed, ranking = run_halite(commands, choice(MAP_SIZES))

    update_ratings(bots, ranking)

    write_db()
    pprint(db)


def run_1v1():
    bots = ['savvybot', 'smarterbot']

    ratings = []
    for bot in bots:
        try:
            rating = Rating(db[bot]['mu'], db[bot]['sigma'])
        except KeyError:
            rating = Rating()
            db[bot] = {
                'mu': rating.mu,
                'sigma': rating.sigma,
            }

        ratings.append(rating)

    commands = ['bots/available/%s/run.sh' % bot for bot in bots]
    map_size = choice(MAP_SIZES)
    seed, ranking1 = run_halite(commands, map_size)

    seed, ranking2 = run_halite(list(reversed(commands)), map_size, seed=seed)

    if ranking1[0] == ranking2[0]:
        updated = rate_1vs1(ratings[0], ratings[1], drawn=True)
        print("draw")
    elif ranking1[0] == 1:
        updated = rate_1vs1(ratings[0], ratings[1])
        print(bots[0])
    else:
        updated = rate_1vs1(ratings[1], ratings[0])
        print(bots[1])

    for bot, rating in zip(bots, updated):
        db[bot]['mu'] = rating.mu
        db[bot]['sigma'] = rating.sigma

    write_db()
    pprint(db)


def update_ratings(bots, ranking):
    ratings = []
    for bot in bots:
        try:
            rating = Rating(db[bot]['mu'], db[bot]['sigma'])
        except KeyError:
            rating = Rating()
            db[bot] = {
                'mu': rating.mu,
                'sigma': rating.sigma,
            }

        ratings.append(tuple([rating]))

    updated = rate(ratings, ranks=ranking)

    for bot, rating in zip(bots, updated):
        db[bot]['mu'] = rating[0].mu
        db[bot]['sigma'] = rating[0].sigma


def write_db():
    with open(DB_FILENAME, 'w') as dbfile:
        dump(db, dbfile)

if __name__ == '__main__':
    try:
        with open(DB_FILENAME) as dbfile:
            db = load(dbfile)
    except FileNotFoundError:
        db = {}
        write_db()

    while True:
        print("Running match...")
        run_match()
