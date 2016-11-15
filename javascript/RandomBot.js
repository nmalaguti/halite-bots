const {
  Move,
  CARDINALS
} = require('./hlt');
const Networking = require('./networking');
const { isEqual, uniqWith, sortBy, first, last } = require('lodash');
const { Graph, astar } = require('javascript-astar');
const fs = require('fs');

const logFileStream = fs.createWriteStream('mylog.log');

let counter = 0;

class LoopAroundGraph extends Graph {
  constructor(gameMap, id) {
    const gridIn = [];

    for (let x = 0; x < gameMap.width; x++) {
      const row = [];
      for (let y = 0; y < gameMap.height; y++) {
        const loc = { x, y };
        const { owner, strength, production } = gameMap.getSite(loc);
        if (owner === id) {
          row.push(512 - production);
        } else {
          row.push(256 - production + strength);
        }
      }
      gridIn.push(row);
    }

    // create grid
    super(gridIn);

    this.gameMap = gameMap;
    this.id = id;
  }

  neighbors(node) {
    return CARDINALS.map((direction) => {
      const { x, y } = this.gameMap.getLocation(node, direction);
      return this.grid[x][y];
    });
  }
}

function bfsClosestEdge(gameMap, id, loc) {
  const nodes = [];
  for (let y = 0; y < gameMap.height; y++) {
    const row = [];
    for (let x = 0; x < gameMap.width; x++) {
      const site = gameMap.getSite({ x, y });
      site.x = x;
      site.y = y;
      site.visited = false;
      row.push(site);
    }
    nodes.push(row);
  }

  function neighbors(loc) {
    return CARDINALS
      .map((direction) => gameMap.getLocation(loc, direction))
      .map(({ x, y }) => nodes[y][x])
      .filter((node) => !node.visited);
  }

  const { x, y } = loc;

  const queue = [];

  queue.push(nodes[y][x]);
  while (queue.length > 0) {
    const current = queue.shift();
    current.visited = true;

    if (current.owner !== id) {
      return current;
    }

    neighbors(current).forEach((neighbor) => {
      neighbor.visited = true;
      queue.push(...neighbors(neighbor));
    });
  }
}

const network = new Networking('MyRandomBot');

network.on('map', (gameMap, id) => {
  logFileStream.write(`\n===== counter: ${counter++} =====\n\n`);
  const moves = [];

  forEachSite(gameMap, ({ site, loc }) => {
    if (site.owner === id && site.strength > 15) {
      const graph = new LoopAroundGraph(gameMap, id);

      const borders = CARDINALS
        .map((direction) => gameMap.getLocation(loc, direction))
        .map((loc) => ({ loc, site: gameMap.getSite(loc) }))
        .filter(({ site }) => site.owner !== id);

      let target;

      if (borders.length > 0) {
        // if on the border, attack the border
        const borderTarget = last(sortBy(
          borders,
          (entry) => entry.site.production,
          (entry) => -entry.site.strength));

        if (site.strength > borderTarget.site.strength) {
          target = borderTarget.loc;
        }
      } else {
        // if in the interior, find the closest edge and go there
        const edgeLoc = bfsClosestEdge(gameMap, id, loc) || { x: 0, y: 0 };

        const path = astar.search(
          graph,
          graph.grid[loc.x][loc.y],
          graph.grid[edgeLoc.x][edgeLoc.y],
          { heuristic: (l1, l2) => gameMap.getDistance(l1, l2) }
        );

        if (path.length) {
          target = first(path);
        }
      }

      if (target) {
        const { x, y } = target;

        CARDINALS.forEach((direction) => {
          const nextLoc = gameMap.getLocation(loc, direction);
          if (isEqual(nextLoc, { x, y })) {
            moves.push(new Move(loc, direction));
          }
        });
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
