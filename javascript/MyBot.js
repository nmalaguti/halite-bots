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
          row.push(256 - production);
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
  logFileStream.write(`bfs: ${JSON.stringify(loc, null, 2)}\n`);
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

const network = new Networking('MyJavaScriptBot');

network.on('map', (gameMap, id) => {
  logFileStream.write(`\n===== counter: ${counter++} =====\n\n`);
  const moves = [];

  // const lowest = findLowestCostPerimeter(gameMap, id);
  // logFileStream.write(`lowest: ${JSON.stringify(lowest, null, 2)}\n`);

  // find best location on map
  const sites = [];
  forEachSite(gameMap, ({ site, loc }) => {
    if (site.owner !== id) sites.push({ site, loc });
  });

  forEachSite(gameMap, ({ site, loc }) => {
    if (site.owner === id && site.strength > 5) {
      const graph = new LoopAroundGraph(gameMap, id);

      const valByDist = sites.map(({ site: site2, loc: loc2 }) => {
        const value = site2.production / Math.log(gameMap.getDistance(loc, loc2));
        return {
          value,
          site: site2,
          loc: loc2,
        };
      });

      const target = last(sortBy(valByDist, 'value')).loc;

      const path = astar.search(
        graph,
        graph.grid[loc.x][loc.y],
        graph.grid[target.x][target.y],
        { heuristic: (l1, l2) => gameMap.getDistance(l1, l2) }
      );

      // logFileStream.write(`eval: ${JSON.stringify({loc, site}, null, 2)}\n`);

      // logFileStream.write(`path: ${JSON.stringify(path.map(({x, y}) => ({x, y}), null, 2))}\n`);

      if (path.length) {
        const { x, y } = first(path);
        CARDINALS.forEach((direction) => {
          const nextLoc = gameMap.getLocation(loc, direction);
          if (isEqual(nextLoc, { x, y })) {
            if (site.strength > 5) moves.push(new Move(loc, direction));
          }
        });
      } else {
        logFileStream.write(`no path from (${loc.x}, ${loc.y}) to (${target.x}, ${target.y})\n`);
      }
    }
  });

  // logFileStream.write(`moves: ${JSON.stringify(moves, null, 2)}\n`);

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

function findPerimeter(gameMap, id) {
  const perimeter = [];
  forEachSite(gameMap, ({ site, loc }) => {
    if (site.owner === id) {
      CARDINALS.forEach((direction) => {
        const neighborLoc = gameMap.getLocation(loc, direction);
        const neighbor = gameMap.getSite(neighborLoc);
        if (neighbor.owner !== id) {
          perimeter.push(neighborLoc);
        }
      });
    }
  });

  return uniqWith(perimeter, isEqual);
}

function findLowestCostPerimeter(gameMap, id) {
  const perimeter = findPerimeter(gameMap, id);

  const temp = sortBy(
    perimeter.map((loc) => ({ loc, site: gameMap.getSite(loc) })),
    (entry) => entry.site.production,
    (entry) => -entry.site.strength
  );

  // logFileStream.write(`perimeter: ${JSON.stringify(temp, null, 2)}\n`);

  return last(temp);
}
