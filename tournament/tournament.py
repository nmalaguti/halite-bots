from trueskill import Rating, rate
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


def run_halite(commands, dimension):
    completed_process = run([HALITE, '-q', '-d', '%s %s' % (dimension, dimension), *commands], stdout=PIPE, universal_newlines=True)
    hlt_file, seed, ranking = parse_result(completed_process.stdout, len(commands))
    remove(hlt_file)

    for log in glob('*.log*'):
        remove(log)

    return ranking


def run_match():
    population = listdir('bots')
    bots = sample(population, randint(2, min(len(population), 6)))

    commands = ['bots/%s/run.sh' % bot for bot in bots]
    ranking = run_halite(commands, choice(MAP_SIZES))

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

    write_db()
    pprint(db)


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
