const {
    Move,
    CARDINALS,
    STILL,
    NORTH,
    WEST,
} = require('./hlt');
const Networking = require('./networking');

const network = new Networking('ImproveBot');

let gameMap;
let id;

function move(loc) {
    const site = gameMap.getSite(loc);
    for (let d of CARDINALS) {
        const neighborSite = gameMap.getSite(loc, d);
        if (neighborSite.owner != id && neighborSite.strength < site.strength) {
            return new Move(loc, d);
        }
    }

    if (site.strength < (site.production * 5)) {
        return new Move(loc, STILL);
    }

    return new Move(loc, Math.random() > 0.5 ? NORTH : WEST);
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
