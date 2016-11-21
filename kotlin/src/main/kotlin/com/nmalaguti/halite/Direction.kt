package com.nmalaguti.halite

import java.util.Random

enum class Direction {
    STILL, NORTH, EAST, SOUTH, WEST;

    companion object {
        val DIRECTIONS = arrayOf(STILL, NORTH, EAST, SOUTH, WEST)
        val CARDINALS = arrayOf(NORTH, EAST, SOUTH, WEST)

        private fun fromInteger(value: Int): Direction? {
            if (value == 0) {
                return STILL
            }
            if (value == 1) {
                return NORTH
            }
            if (value == 2) {
                return EAST
            }
            if (value == 3) {
                return SOUTH
            }
            if (value == 4) {
                return WEST
            }
            return null
        }

        fun randomDirection(): Direction {
            return fromInteger(Random().nextInt(5))!!
        }
    }
}
