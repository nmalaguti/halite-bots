#!/bin/bash

(
    cd java
    javac MyBot.java
)
(
    cd java
    javac RandomBot.java
)
./halite -d "30 30" "java -cp ./java MyBot" "java -cp ./java RandomBot"
