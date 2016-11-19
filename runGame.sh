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

./halite -d "50 50" -s 2773619301 "java -cp ./java MyBot" "python hacked-javascript/MyBot.py" "python kotlin/build/install/MyBot/MyBot.py"
