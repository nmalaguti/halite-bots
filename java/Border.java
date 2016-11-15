public class Border implements Comparable<Border> {
    public Location loc;
    public Site site;
    public Direction direction;

    public Border(Location loc, Site site, Direction direction) {
        this.loc = loc;
        this.site = site;
        this.direction = direction;
    }

    @Override
    public int compareTo(Border other) {
        // compareTo should return < 0 if this is supposed to be
        // less than other, > 0 if this is supposed to be greater than
        // other and 0 if they are supposed to be equal

        if (this.site.production != other.site.production) {
            return Integer.compare(this.site.production, other.site.production);
        } else if (this.site.strength != other.site.strength) {
            return Integer.compare(this.site.strength, other.site.strength) * -1;
        } else {
            return 0;
        }
    }
}
