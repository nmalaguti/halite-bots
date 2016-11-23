package com.nmalaguti.halite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Networking {
    public static final int SIZE_OF_INTEGER_PREFIX = 4;
    public static final int CHAR_SIZE = 1;
    private static int _width, _height;
    private static ArrayList< ArrayList<Integer> > _productions;

    static void deserializeGameMapSize(String inputString) {
        String[] inputStringComponents = inputString.split(" ");

        _width = Integer.parseInt(inputStringComponents[0]);
        _height = Integer.parseInt(inputStringComponents[1]);
    }


    static void deserializeProductions(String inputString) {
        String[] inputStringComponents = inputString.split(" ");

        int index = 0;
        _productions = new ArrayList<>();
        for(int a = 0; a < _height; a++) {
            ArrayList<Integer> row = new ArrayList<Integer>();
            for(int b = 0; b < _width; b++) {
                row.add(Integer.parseInt(inputStringComponents[index]));
                index++;
            }
            _productions.add(row);
        }
    }

    static String serializeMoveList(Collection<Move> moves) {
        StringBuilder builder = new StringBuilder();
        for(Move move : moves) builder.append(move.getLoc().getX() + " " + move.getLoc().getY() + " " + move.getDir().ordinal() + " ");
        return builder.toString();
    }

    static GameMap deserializeGameMap(String inputString) {
        String[] inputStringComponents = inputString.split(" ");

        GameMap map = new GameMap(_width, _height);

        // Run-length encode of owners
        int y = 0, x = 0;
        int counter = 0, owner = 0;
        int currentIndex = 0;
        while(y != map.height) {
            counter = Integer.parseInt(inputStringComponents[currentIndex]);
            owner = Integer.parseInt(inputStringComponents[currentIndex + 1]);
            currentIndex += 2;
            for(int a = 0; a < counter; ++a) {
                map.contents.get(y).get(x).setOwner(owner);
                ++x;
                if(x == map.width) {
                    x = 0;
                    ++y;
                }
            }
        }

        for (int a = 0; a < map.contents.size(); ++a) {
            for (int b = 0; b < map.contents.get(a).size(); ++b) {
                int strengthInt = Integer.parseInt(inputStringComponents[currentIndex]);
                currentIndex++;
                map.contents.get(a).get(b).setStrength(strengthInt);
                map.contents.get(a).get(b).setProduction(_productions.get(a).get(b));
            }
        }

        return map;
    }

    static void sendString(String sendString) {
        System.out.print(sendString+'\n');
        System.out.flush();
    }

    static String getString() {
        try {
            StringBuilder builder = new StringBuilder();
            int buffer;
            while ((buffer = System.in.read()) >= 0) {
                if (buffer == '\n') {
                    break;
                } else {
                    builder = builder.append((char)buffer);
                }
            }
	    if(builder.charAt(builder.length()-1) == '\r') builder.setLength(builder.length()-1); //Removes a carriage return if on windows for manual testing.
            return builder.toString();
        } catch(Exception e) {
            System.exit(1);
            return null; // the java compiler is stupid
        }
    }

    static InitPackage getInit() {

        int myID = Integer.parseInt(getString());
        deserializeGameMapSize(getString());
        deserializeProductions(getString());
        GameMap gameMap = deserializeGameMap(getString());

        InitPackage initPackage = new InitPackage(myID, gameMap);

        return initPackage;
    }

    static void sendInit(String name) {
        sendString(name);
    }

    static GameMap getFrame() {
        return deserializeGameMap(getString());
    }

    static void sendFrame(Collection<Move> moves) {
        sendString(serializeMoveList(moves));
    }

}
