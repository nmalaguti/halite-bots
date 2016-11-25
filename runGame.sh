#!/bin/bash

rm -f *.log* *.hlt

(
    cd kotlin
    ./gradlew installDist
)

# -s 515525598

./halite -d "40 40" \
    "tournament/bots/available/frugalbot/run.sh" \
    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \



#    "tournament/bots/available/smarterbot/run.sh" \
#    "tournament/bots/available/battlebot/run.sh" \
#    "tournament/bots/available/betterbot/run.sh" \




#    "tournament/bots/available/betterbot/run.sh" \
#    "java -jar stable/betterbot/MyBot.jar" \
#    "java -cp ./stable/javabot MyBot" \
#    "python stable/jsbot/MyBot.py" \
#    "python stable/kotlinbot/MyBot.py" \
