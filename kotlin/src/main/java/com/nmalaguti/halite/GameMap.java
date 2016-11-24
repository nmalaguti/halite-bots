package com.nmalaguti.halite;

import java.util.ArrayList;

public class GameMap{
    public ArrayList< ArrayList<Site> > contents;
    public int width, height;

    public GameMap() {
        width = 0;
        height = 0;
        contents = new ArrayList<>(0);
    }

    public GameMap(int width_, int height_) {
        width = width_;
        height = height_;
        contents = new ArrayList<>(0);
        for(int y = 0; y < height; y++) {
            ArrayList<Site> row = new ArrayList<>();
            for(int x = 0; x < width; x++) {
                row.add(new Site(0, 0, 0, new Location(x, y)));
            }
            contents.add(row);
        }
    }

    public GameMap(GameMap gameMap) {
        width = gameMap.width;
        height = gameMap.height;
        contents = new ArrayList<>(0);
        for (ArrayList<Site> row : gameMap.contents) {
            ArrayList<Site> newRow = new ArrayList<>();
            for (Site site : row) {
                newRow.add(new Site(site.getStrength(), site.getProduction(), site.getOwner(), new Location(site.getLoc())));
            }
            contents.add(newRow);
        }
    }

    public boolean inBounds(Location loc) {
        return loc.getX() < width && loc.getX() >= 0 && loc.getY() < height && loc.getY() >= 0;
    }

    public double getDistance(Location loc1, Location loc2) {
        int dx = Math.abs(loc1.getX() - loc2.getX());
        int dy = Math.abs(loc1.getY() - loc2.getY());

        if(dx > width / 2.0) dx = width - dx;
        if(dy > height / 2.0) dy = height - dy;

        return dx + dy;
    }

    public double getAngle(Location loc1, Location loc2) {
        int dx = loc1.getX() - loc2.getX();

        // Flip order because 0,0 is top left
        // and want atan2 to look as it would on the unit circle
        int dy = loc2.getY() - loc1.getY();

        if(dx > width - dx) dx -= width;
        if(-dx > width + dx) dx += width;

        if(dy > height - dy) dy -= height;
        if(-dy > height + dy) dy += height;

        return Math.atan2(dy, dx);
    }

    public Location getLocation(Location loc, Direction dir) {
        Location l = new Location(loc);
        if(dir != Direction.STILL) {
            if(dir == Direction.NORTH) {
                if(l.getY() == 0) l.setY(height - 1);
                else l.setY(l.getY() - 1);
            }
            else if(dir == Direction.EAST) {
                if(l.getX() == width - 1) l.setX(0);
                else l.setX(l.getX() + 1);
            }
            else if(dir == Direction.SOUTH) {
                if(l.getY() == height - 1) l.setY(0);
                else l.setY(l.getY() + 1);
            }
            else if(dir == Direction.WEST) {
                if(l.getX() == 0) l.setX(width - 1);
                else l.setX(l.getX() - 1);
            }
        }
        return l;
    }

    public Site getSite(Location loc, Direction dir) {
        Location l = getLocation(loc, dir);
        return contents.get(l.getY()).get(l.getX());
    }

    public Site getSite(Location loc) {
        return contents.get(loc.getY()).get(loc.getX());
    }
}
