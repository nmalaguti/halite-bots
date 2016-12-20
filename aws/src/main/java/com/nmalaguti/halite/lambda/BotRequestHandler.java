package com.nmalaguti.halite.lambda;

import com.amazonaws.services.lambda.runtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.nmalaguti.halite.Move;
import com.nmalaguti.halite.bots.Bot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class BotRequestHandler implements RequestStreamHandler {

    protected Constructor constructor;

    public BotRequestHandler(Class klass) {
        super();

        Constructor[] constructors = klass.getConstructors();
        if (constructors.length > 1) {
            throw new IllegalArgumentException();
        }
        constructor = constructors[0];
    }

    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule());
    private static final Logger logger = LoggerFactory.getLogger(BotRequestHandler.class);

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        logger.debug("Start request");

        Request request = mapper.readValue(input, Request.class);

        try {
            Bot bot = (Bot)constructor.newInstance(request.getId(), request.getPreviousMoves(), request.getGameMap());

            List<Move> moves = bot.runOnce();

            Response response = new Response(moves);

            logger.debug("End request");

            mapper.writeValue(output, response);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
