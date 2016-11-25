#!/usr/bin/env bash

# install halite
if [[ ! -f halite ]]; then
    sh -c "$(curl -fsSL https://raw.githubusercontent.com/HaliteChallenge/Halite/master/environment/install.sh)"
fi

if [[ ! -d venv ]]; then
    virtualenv -p python3 venv
fi

source ./venv/bin/activate

pip install -r requirements.txt

python tournament.py

