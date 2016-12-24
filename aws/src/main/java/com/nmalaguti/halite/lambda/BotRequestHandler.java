package com.nmalaguti.halite.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.nmalaguti.halite.bots.DoubleBot;
import com.nmalaguti.halite.bots.FrugalBot;
import com.nmalaguti.halite.bots.ThugBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class BotRequestHandler implements RequestStreamHandler {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(BotRequestHandler.class);

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        logger.debug("Start request");

        Request request = mapper.readValue(input, Request.class);
        RequestPayload requestPayload = mapper.readValue(request.getBody(), RequestPayload.class);

        Response response;

        String selectedBot = request.getPathParameters().get("botName");

        if (selectedBot == null) {
            response = new Response(404, new HashMap<>(), "");
        } else if (selectedBot.equalsIgnoreCase("alice")) {
            DoubleBot bot = new DoubleBot(requestPayload.getId(), requestPayload.getPreviousMoves(), requestPayload.getGameMap());
            ResponsePayload responsePayload = new ResponsePayload(bot.runOnce());

            response = new Response(200, new HashMap<>(), mapper.writeValueAsString(responsePayload));
        } else if (selectedBot.equalsIgnoreCase("bridget")) {
            FrugalBot bot = new FrugalBot(requestPayload.getId(), requestPayload.getPreviousMoves(), requestPayload.getGameMap());
            ResponsePayload responsePayload = new ResponsePayload(bot.runOnce());

            response = new Response(200, new HashMap<>(), mapper.writeValueAsString(responsePayload));
        } else if (selectedBot.equalsIgnoreCase("cathy")) {
            ThugBot bot = new ThugBot(requestPayload.getId(), requestPayload.getPreviousMoves(), requestPayload.getGameMap());
            ResponsePayload responsePayload = new ResponsePayload(bot.runOnce());

            response = new Response(200, new HashMap<>(), mapper.writeValueAsString(responsePayload));
        } else {
            response = new Response(404, new HashMap<>(), "");
        }

        logger.debug("End request");

        mapper.writeValue(output, response);
    }
}
