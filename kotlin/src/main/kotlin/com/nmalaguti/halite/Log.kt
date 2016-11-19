package com.nmalaguti.halite

import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

val fileHandler = FileHandler("MyKotlinBot.log")
val formatter = SimpleFormatter()
val logger: Logger = Logger.getLogger("MyBetterBot")
