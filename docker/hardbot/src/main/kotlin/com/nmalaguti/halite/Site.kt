package com.nmalaguti.halite

class Site(var strength: Int = 0, var production: Int = 0, var owner: Int = 0) {
    override fun toString() = "strength: $strength, production: $production, owner: $owner"
}
