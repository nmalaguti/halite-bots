const {
  Move,
  CARDINALS
} = require('./hlt');
const Networking = require('./networking');
const { sortBy, first, last } = require('lodash');
const fs = require('fs');

const logFileStream = fs.createWriteStream('notrandom.log');

let counter = 0;

function straightClosestEdge(gameMap, id, loc) {
  const directions = CARDINALS.map((direction) => {
    let distance = 0;
    let newLoc = loc;
    let newSite;

    do {
      distance++;
      newLoc = gameMap.getLocation(newLoc, direction);
      newSite = gameMap.getSite(newLoc);
    } while (newSite.owner === id && distance < Math.min(gameMap.width, gameMap.height) / 2);

    return {
      direction,
      distance
    };
  });

  return first(sortBy(directions, 'distance')).direction;
}

const network = new Networking('NotRandomBot');

network.on('map', (gameMap, id) => {
  logFileStream.write(`\n===== counter: ${counter++} =====\n\n`);
  const moves = [];

  forEachSite(gameMap, ({ site, loc }) => {
    if (site.owner === id && site.strength > 15) {

      const neighbors = CARDINALS.map((direction) => {
        const newLoc = gameMap.getLocation(loc, direction);
        const newSite = gameMap.getSite(newLoc);

        return {
          loc: newLoc,
          site: newSite,
          direction
        };
      });

      const borders = neighbors.filter(({ site }) => site.owner !== id);

      if (borders.length > 0) {
        // if on the border, attack the border
        const borderTarget = last(sortBy(
          borders,
          (entry) => entry.site.production,
          (entry) => -entry.site.strength));

        if (site.strength > borderTarget.site.strength) {
          moves.push(new Move(loc, borderTarget.direction));
        }
      } else {
        // if in the interior, find the closest edge and go there
        const direction = straightClosestEdge(gameMap, id, loc);
        moves.push(new Move(loc, direction));
      }
    }
  });

  network.sendMoves(moves);
});

function forEachSite(gameMap, cb) {
  for (let y = 0; y < gameMap.height; y++) {
    for (let x = 0; x < gameMap.width; x++) {
      const loc = { x, y };
      cb({ site: gameMap.getSite(loc), loc });
    }
  }
}
