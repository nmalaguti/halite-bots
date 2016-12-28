#!/bin/bash

rm -f *.log* *.hlt

(
    cd kotlin
    ./gradlew installDist
)

##########################

./halite -d "35 35" \
    "tournament/bots/available/seekerbot/run.sh" \
    "tournament/bots/available/orientationbot/run.sh" \
    "tournament/bots/available/orientationbot/run.sh" \
    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2024742149 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2024742149 \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1622520692 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1622520692 \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1065657776 \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "50 50" -s 1808075107 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "50 50" -s 4040479613 \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# lots of prod = 0. good for testing 255 constrained combat
#./halite -d "35 35" -s 574898733 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3614115138 \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2811877567 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/seekerbot/run.sh" \

#./halite -d "35 35" -s 4030606183 \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1006313739 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# used for optimizing sort order for targets
#./halite -d "35 35" -s 1286846703 \
#    "tournament/bots/available/fighterbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/superchargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# investigate order of neighbors
#./halite -d "35 35" -s 2491474543 \
#    "tournament/bots/available/fighterbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/superchargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3933202914 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/fighterbot/run.sh" \

#./halite -d "45 45" -s 1119897993 \
#    "tournament/bots/available/fighterbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/superchargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#    "tournament/bots/available/fighterbot/run.sh" \


#./halite -d "35 35" -s 1467275615 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/fighterbot/run.sh" \

#./halite -d "35 35" -s 1588947454 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/superchargerbot/run.sh" \
#    "tournament/bots/available/expansionbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 477259113 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 124078157 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# won when I didn't follow production
#./halite -d "35 35" -s 3593375754 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "45 45" -s 3301690963 \
#    "tournament/bots/available/superchargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1823590726 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/chargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# average bots do better
#./halite -d "35 35" -s 2023371628 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/averagebot/run.sh" \
#    "tournament/bots/available/chargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 520813577 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/averagebot/run.sh" \
#    "tournament/bots/available/chargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2627115728 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/averagebot/run.sh" \
#    "tournament/bots/available/chargerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 184028778 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "40 40" -s 2160309718 \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \


#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/fixedbot/run.sh" \



#    "tournament/bots/available/fixedbot/run.sh" \

#    "tournament/bots/available/swarmbot/run.sh" \
#    "tournament/bots/available/frugalbot/run.sh" \
#    "java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005,quiet=y -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#    "tournament/bots/available/smarterbot/run.sh" \

#    "node javascript/OverkillBot.js" \
#    "tournament/bots/available/frugalbot/run.sh" \
#    "tournament/bots/available/smarterbot/run.sh" \
#    "tournament/bots/available/battlebot/run.sh" \




#    "tournament/bots/available/betterbot/run.sh" \
#    "java -jar stable/betterbot/MyBot.jar" \
#    "java -cp ./stable/javabot MyBot" \
#    "python stable/jsbot/MyBot.py" \
#    "python stable/kotlinbot/MyBot.py" \
