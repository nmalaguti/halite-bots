#!/usr/bin/env bash

cd "$(dirname "$0")"


mkdir -p "bots/available/$1"
cp bots/available/frugalbot/run.sh "bots/available/$1/"
(cd ../kotlin; gw installDist;)
cp ../kotlin/build/install/MyBot/MyBot.jar "bots/available/$1/"
ssh nmalaguti.com "mkdir -p ~/git/halite-bots/tournament/bots/available/$1"
scp bots/available/$1/* nmalaguti.com:git/halite-bots/tournament/bots/available/$1/
ssh nmalaguti.com "cd ~/git/mini-halite && source venv/bin/activate && python manage.py addbot $1"
