package com.nmalaguti.halite.bots

import com.nmalaguti.halite.Move

interface Bot {
    fun runOnce(): List<Move>
}
