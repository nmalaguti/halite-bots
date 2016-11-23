#!/usr/bin/env bash

# install halite
sh -c "$(curl -fsSL https://raw.githubusercontent.com/HaliteChallenge/Halite/master/environment/install.sh)"

virtualenv -p python3 venv
source ./venv/bin/activate

pip install -r requirements.txt

python tournament.py

