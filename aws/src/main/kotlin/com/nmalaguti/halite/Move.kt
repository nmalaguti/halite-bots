package com.nmalaguti.halite

import com.fasterxml.jackson.annotation.JsonFormat

data class Move(val loc: Location,
                @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
                val dir: Direction)
