package com.nmalaguti.halite

data class Location(var x: Int, var y: Int) {
    constructor(location: Location) : this(location.x, location.y)
}
