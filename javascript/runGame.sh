#!/bin/bash

rm -f *.log* *.hlt

../halite -d "50 50" \
    "node ImproveBot.js" \
    "node DeterministicBot.js" \
    "node AmbiturnerBot.js" \
    "node ProductionBot.js" \
    "node OverkillBot.js" \


#    "node ProductionBot.js" \
