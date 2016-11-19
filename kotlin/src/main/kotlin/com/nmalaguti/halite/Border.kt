package com.nmalaguti.halite

class Border(val loc: Location, val site: Site, val direction: Direction) : Comparable<Border> {
    override fun compareTo(other: Border): Int {
        // compareTo should return < 0 if this is supposed to be
        // less than other, > 0 if this is supposed to be greater than
        // other and 0 if they are supposed to be equal

        if (this.site.production != other.site.production) {
            return Integer.compare(this.site.production, other.site.production)
        } else if (this.site.strength != other.site.strength) {
            return Integer.compare(this.site.strength, other.site.strength) * -1
        } else {
            return 0
        }
    }
}
