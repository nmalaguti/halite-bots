#!/bin/bash

rm -f *.log *.hlt

(
    cd java
    javac MyBot.java
)
(
    cd kotlin
    ./gradlew installDist
)

./halite -d "50 50" \
    "java -cp ./stable/javabot MyBot" \
    "python stable/jsbot/MyBot.py" \
    "python stable/kotlinbot/MyBot.py" \
    "python kotlin/build/install/MyBot/MyBot.py"
