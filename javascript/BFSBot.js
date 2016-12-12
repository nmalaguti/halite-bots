const {
    Move,
    CARDINALS,
    STILL,
    NORTH,
    WEST,
} = require('./hlt');
const Networking = require('./networking');
const _ = require('lodash');

const network = new Networking('BFSBot');

let gameMap;
let id;

function getNeighbors(loc) {
    return CARDINALS.map((direction) => ({
        loc: gameMap.getLocation(loc, direction),
        site: gameMap.getSite(loc, direction),
        direction
    }));
}

function getFriends(loc) {
    return getNeighbors(loc).filter((neighbor) => neighbor.site.owner === id);
}

function resource(loc) {
    const site = gameMap.getSite(loc);
    if (site.owner !== id) {
        return site.strength / site.production;
    }

    // use a large value that will be replaced by a neighbor
    return 9999;
}

function isOuterBorder(loc) {
    const site = gameMap.getSite(loc);

    // location isn't mine but neighbors mine
    return site.owner !== id && getFriends(loc).length > 0;
}

function buildGrid() {
    const grid = [];
    for (let y = 0; y < gameMap.height; y++) {
        const row = [];
        for (let x = 0; x < gameMap.width; x++) {
            row.push(resource({ x, y }));
        }
        grid.push(row);
    }

    const border = [];
    for (let y = 0; y < gameMap.height; y++) {
        for (let x = 0; x < gameMap.width; x++) {
            const loc = { x, y };
            if (isOuterBorder(loc)) {
                border.push(loc);
            }
        }
    }

    walkGrid(grid, _.chain(border)
        .orderBy((loc) => grid[loc.y][loc.x])
        // take some starting points
        .take(5)
        .value());

    return grid;
}

function walkGrid(grid, locs) {
    const openSet = new Set(locs.map((loc) => JSON.stringify(loc)));
    const closedSet = new Set();

    while (openSet.size > 0) {
        const current = _.first(Array.from(openSet.values()));
        openSet.delete(current);

        if (!closedSet.has(current)) {
            closedSet.add(current);

            const currentLoc = JSON.parse(current);
            const currentSite = gameMap.getSite(currentLoc);

            if (currentSite.owner === id) {
                grid[currentLoc.y][currentLoc.x] = Math.min(
                    grid[currentLoc.y][currentLoc.x],
                    1 + Math.min(...getNeighbors(currentLoc)
                        .map((neighbor) => grid[neighbor.loc.y][neighbor.loc.x]))
                );
            }

            getNeighbors(currentLoc)
                .filter((neighbor) => !(neighbor.site.owner === 0 && neighbor.site.strength > 0))
                .forEach((neighbor) => {
                    openSet.add(JSON.stringify(neighbor.loc));
                });
        }
    }

}

function heuristic(cell) {
    if (cell.site.owner === 0 && cell.site.strength > 0) {
        return cell.site.production / cell.site.strength;
    } else {
        // attacking an enemy
        let totalDamage = 0;
        for (let d of CARDINALS) {
            let site = gameMap.getSite(cell.loc, d);
            if (site.owner !== 0 && site.owner !== id) {
                totalDamage += site.strength;
            }
        }

        return totalDamage;
    }
}

// I've taken a dependency on lodash https://lodash.com
function move(grid, loc) {
    const site = gameMap.getSite(loc);

    if (site.strength === 0) {
        return new Move(loc, STILL);
    }

    const target = _.chain(getNeighbors(loc))
        // filter only the neighbors that have better positions than us
        .filter((neighbor) => grid[neighbor.loc.y][neighbor.loc.x] < grid[loc.y][loc.x])
        .filter((neighbor) => {
            if (neighbor.site.owner === 0 && neighbor.site.strength === 0) {
                // space between players
                // attack!
                return true;
            } else if (neighbor.site.owner === 0) {
                // environment square
                // make sure we have the strength to take it
                return neighbor.site.strength < site.strength;
            } else {
                // mine
                return site.strength >= (site.production * 5);
            }
        })
        .orderBy((neighbor) => heuristic(neighbor), ['desc'])
        .first()
        .value();

    if (target) {
        return new Move(loc, target.direction);
    }

    // otherwise wait
    return new Move(loc, STILL);
}

network.on('map', (gm, myId) => {
    gameMap = gm;
    id = myId;

    const moves = [];
    const grid = buildGrid();

    for (let y = 0; y < gameMap.height; y++) {
        for (let x = 0; x < gameMap.width; x++) {
            const loc = { x, y };
            const { owner } = gameMap.getSite(loc);
            if (owner === id) {
                moves.push(move(grid, loc));
            }
        }
    }

    network.sendMoves(moves);
});
