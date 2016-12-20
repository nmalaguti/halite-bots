package com.nmalaguti.halite.lambda

import com.nmalaguti.halite.GameMap
import com.nmalaguti.halite.Move

class Request(val id: Int, val previousMoves: List<Move>, val gameMap: GameMap)
