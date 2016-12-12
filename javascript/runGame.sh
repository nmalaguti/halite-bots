#!/bin/bash

rm -f *.log* *.hlt

../halite -d "50 50" \
    "node OverkillBot.js" \
    "node BFSBot.js" \


#    "node ProductionBot.js" \
