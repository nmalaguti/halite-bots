#!/usr/bin/env bash

cd "$(dirname "$0")"

mkdir "bots/available/$1"
cp bots/available/frugalbot/run.sh "bots/available/$1/"
cp ../kotlin/build/install/MyBot/MyBot.jar "bots/available/$1/"
ssh nmalaguti.com "mkdir ~/git/halite-bots/tournament/bots/available/$1"
scp bots/available/$1/* nmalaguti.com:git/halite-bots/tournament/bots/available/$1/
