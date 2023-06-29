const {
    Move,
    CARDINALS,
    STILL,
    NORTH,
    WEST,
} = require('./hlt');
const Networking = require('./networking');
const _ = require('lodash');


const network = new Networking('NoviceBot');

let gameMap;
let id;

function getNeighbors(loc) {
    return CARDINALS.map((direction) => ({
        loc: gameMap.getLocation(loc, direction),
        site: gameMap.getSite(loc, direction),
        direction
    }));
}

function getEnemies(loc) {
    return getNeighbors(loc).filter((neighbor) => neighbor.site.owner !== id);
}

function getFriends(loc) {
    return getNeighbors(loc).filter((neighbor) => neighbor.site.owner === id);
}

function findNearestEnemyDirection(loc) {
    let direction = NORTH;
    let maxDistance = Math.min(gameMap.width, gameMap.height) / 2;

    for (let d of CARDINALS) {
        let distance = 0;
        let current = loc;
        let site = gameMap.getSite(current, d);
        while (site.owner == id && distance < maxDistance) {
            distance++;
            current = gameMap.getLocation(current, d);
            site = gameMap.getSite(current);
        }

        if (distance < maxDistance) {
            direction = d;
            maxDistance = distance;
        }
    }

    return direction;
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
function move(loc) {
    const site = gameMap.getSite(loc);

    const target = _.chain(CARDINALS)
        .map((direction) => ({
            direction,
            loc: gameMap.getLocation(loc, direction),
            site: gameMap.getSite(loc, direction)
        }))
        // only enemy cells
        .filter((cell) => cell.site.owner !== id)
        // sort by production descending
        .orderBy([(cell) => heuristic(cell)], ['desc'])
        .first()
        .value();

    if (target && target.site.strength < site.strength) {
        return new Move(loc, target.direction);
    }

    if (site.strength < (site.production * 5)) {
        return new Move(loc, STILL);
    }

    // if the cell isn't on the border
    if (CARDINALS.every((d) => gameMap.getSite(loc, d).owner === id)) {
        return new Move(loc, findNearestEnemyDirection(loc));
    }

    // otherwise wait until you can attack
    return new Move(loc, STILL);
}

network.on('map', (gm, myId) => {
    gameMap = gm;
    id = myId;

    const moves = [];

    for (let y = 0; y < gameMap.height; y++) {
        for (let x = 0; x < gameMap.width; x++) {
            const loc = { x, y };
            const { owner } = gameMap.getSite(loc);
            if (owner === id) {
                moves.push(move(loc));
            }
        }
    }

    network.sendMoves(moves);
});
