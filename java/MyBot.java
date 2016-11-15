import java.util.*;

public class MyBot {
    public static void main(String[] args) throws java.io.IOException {
        InitPackage iPackage = Networking.getInit();
        int id = iPackage.myID;
        GameMap gameMap = iPackage.map;

        Networking.sendInit("MyJavaBot");

        while(true) {
            ArrayList<Move> moves = new ArrayList<Move>();

            gameMap = Networking.getFrame();

            for(int y = 0; y < gameMap.height; y++) {
                for(int x = 0; x < gameMap.width; x++) {
                    Move move = makeMove(x, y, gameMap, id);
                    if (move != null) {
                        moves.add(move);
                    }
                }
            }
            Networking.sendFrame(moves);
        }
    }

    public static Move makeMove(int x, int y, GameMap gameMap, int id) {
        Location loc = new Location(x, y);
        Site site = gameMap.getSite(loc);
        if(site.owner == id && site.strength > 15) {
            List<Border> borders = new ArrayList<Border>();
            for (Direction direction : Direction.CARDINALS) {
                Location newLoc = gameMap.getLocation(loc, direction);
                Site newSite = gameMap.getSite(newLoc);

                if (newSite.owner != id) {
                    borders.add(new Border(newLoc, newSite, direction));
                }
            }

            if (borders.size() > 0) {
                Collections.sort(borders);
                Border best = borders.get(borders.size() - 1);
                if (site.strength > best.site.strength) {
                    return new Move(loc, best.direction);
                }
            } else {
                Direction bestDir = straightClosestEdge(gameMap, id, loc);
                return new Move(loc, bestDir);
            }
        }
        return null;
    }

    public static Direction straightClosestEdge(GameMap gameMap, int id, Location loc) {
        Direction selected = Direction.NORTH;
        int selectedDistance = gameMap.width * gameMap.height;

        for (Direction direction : Direction.CARDINALS) {
            int distance = 0;
            Location newLoc = loc;
            Site newSite;
            do {
                distance++;
                newLoc = gameMap.getLocation(newLoc, direction);
                newSite = gameMap.getSite(newLoc);
            } while (newSite.owner == id &&
                    distance < (Math.min(gameMap.width, gameMap.height) / 2) &&
                    distance < selectedDistance);

            if (distance < selectedDistance) {
                selectedDistance = distance;
                selected = direction;
            }
        }

        return selected;
    }
}
