package com.nmalaguti.halite

import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

val fileHandler = FileHandler("$BOT_NAME.log")
val formatter = SimpleFormatter()
val logger: Logger = Logger.getLogger(BOT_NAME)

fun initializeLogging() {
    logger.useParentHandlers = false

    fileHandler.formatter = formatter
    logger.addHandler(fileHandler)
}

fun log(vararg stuff: Any) {
    logger.info(stuff.map { "$it" }.joinToString(" "))
}
