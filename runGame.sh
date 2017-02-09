#!/bin/bash

rm -f *.log* *.hlt

(
    cd kotlin
    ./gradlew installDist
)

##########################

#time ./halite -d "50 50" -s 2115962231 "tournament/bots/available/hungrybot/run.sh" \

# java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005,quiet=y -jar MyBot.jar

# 1108556689
# 1054512155
# 1142537951

# https://halite.io/game.php?replay=ar1483380454-1829563253.hlt
# https://halite.io/game.php?replay=ar1483393284-1775054597.hlt
# https://halite.io/game.php?replay=ar1483393715-2206445183.hlt
# https://halite.io/game.php?replay=ar1484675430-4021084152.hlt

./halite -d "40 40" -s 1242531708 \
    "tournament/bots/available/focusbot/run.sh" \
    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "40 40" -s 2762992480 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/waittoattackbot/run.sh" \
#    "tournament/bots/available/sharpparametermix2bot/run.sh" \
#    "tournament/bots/available/krakenbot/run.sh" \

#./halite -d "30 30" -s 3901829081 \
#    "tournament/bots/available/thugclassicanyduobot/run.sh" \
#    "tournament/bots/available/compressbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -t -d "35 35" -s 1333135258 \
#    "tournament/bots/available/napbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "30 30" -s 694123085 \
#    "tournament/bots/available/compressbugfixbot/run.sh" \
#    "tournament/bots/available/finebattlebotv2-lessbruise/run.sh" \
#    "tournament/bots/available/finebattlebotv2/run.sh" \
#    "tournament/bots/available/superhungrybot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "50 50" -s 3971582041 \
#    "tournament/bots/available/canidobetterbotv19/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "24 24" -s 1681841247 \
#    "tournament/bots/available/canidobetterbotv19/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "40 40" -s 2428252705 \
#    "tournament/bots/available/frankensteinbot/run.sh" \
#    "tournament/bots/available/finebattlebotv2-lessbruise/run.sh" \
#    "tournament/bots/available/fastexpandbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "40 40" -s 193709268 \
#    "tournament/bots/available/finebattlebotv2-nobruiseexpand/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "40 40" -s 146658805 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/finebattlebotv2-lessbruise/run.sh" \

#./halite -d "35 35" -s 3276072529 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" \
#    "tournament/bots/available/superhungrynofixbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2165106391 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/superhungrynofixbot/run.sh" \

#./halite -d "35 35" -s 2927148054 \
#    "tournament/bots/available/finebattlebotv2/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 213949955 \
#    "tournament/bots/available/superhungrynofixbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 41721062 \
#    "tournament/bots/available/superhungrynofixbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "36 45" -s 170883337 \
#    "tournament/bots/available/compressbugfixbot/run.sh" \
#    "tournament/bots/available/spearbot/run.sh" \
#    "tournament/bots/available/compressbugfixbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/seekerbot/run.sh" \
#    "tournament/bots/available/directedalwaysbot/run.sh" \

#./halite -t -d "25 25" -s 262582835 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/orientationbot/run.sh" \

#./halite -d "35 35" -s 3896913109 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 771479765 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "25 25" -s 1054512155 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "25 25" -s 829932397 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "45 45" -s 67699555 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "45 45" -s 2867524945 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2679872778 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1858044664 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1607443210 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 89566946 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/fastexpandbot/run.sh" \

#./halite -d "50 50" -s 3173792174 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/fastexpandbot/run.sh" \

# 3418377502
# 3770836417

#./halite -d "35 35" -s 3418377502 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2155077429 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 934448538 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# TO INVESTIGATE
# https://halite.io/game.php?replay=ar1483315205-1005180436.hlt
# https://halite.io/game.php?replay=ar1483311296-1391725357.hlt
# https://halite.io/game.php?replay=ar1483317151-2951660361.hlt
# https://halite.io/game.php?replay=ar1483320146-1651591812.hlt
# https://halite.io/game.php?replay=ar1483319903-1408168050.hlt

# didn't get there fast enough
# https://halite.io/game.php?replay=ar1483315205-1005180436.hlt
#./halite -d "45 45" -s 1005180436 \
#    "tournament/bots/available/travelerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \

# should have stopped earlier - don't know if that would have saved things
# https://halite.io/game.php?replay=ar1483312320-2415499296.hlt
#./halite -d "35 35" -s 2415499296 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# traveled way too far
# https://halite.io/game.php?replay=ar1483318207-4007485397.hlt
#./halite -d "45 45" -s 4007485397 \
#    "tournament/bots/available/travelerbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# shouldn't have won, my early expansion was bad
# https://halite.io/game.php?replay=ar1483304169-2854665850.hlt
#./halite -d "35 35" -s 2854665850 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# don't know why I didn't go after the hot spot
# https://halite.io/game.php?replay=ar1483317722-3522689094.hlt
# NOT FIXED
#./halite -d "40 40" -s 3522689094 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# should go after near term goals?
# https://halite.io/game.php?replay=ar1483303249-1934049587.hlt
#./halite -d "40 40" -s 1934049587 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# fix global vs local
# https://halite.io/game.php?replay=ar1483303113-1798668336.hlt
#./halite -d "40 40" -s 1798668336 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "40 40" -s 1577469022 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/lesscruftbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "30 30" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "30 30" -s 2225239670 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "45 45" -s 3315401282 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 452484725 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/fighterbot/run.sh" \
#    "tournament/bots/available/lesscruftbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3068068922 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 775989403 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3461107360 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

# investigate ways to avoid going for hot spot in this case - maybe if value is too low?
#./halite -d "45 45" -s 1070861001 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3976104731 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/fighterbot/run.sh" \
#    "tournament/bots/available/lesscruftbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#    "tournament/bots/available/expeditionbot/run.sh" \

#./halite -d "35 35" -s 1564892472 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/lesscruftbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 866759793 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/lesscruftbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3913847278 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/thugbot/run.sh" \
#    "tournament/bots/available/lesscruftbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 3914065167 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2635137072 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 601095258 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "36 39" -s 3293006875 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/travelerbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2699214119 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 4012441361 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1762049356 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2571574913 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/orientationbot/run.sh" \

#./halite -d "35 35" -s 1812621270 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 86467011 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 375732772 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1356193419 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/orientationbot/run.sh" \

#./halite -d "35 35" -s 3424714741 \
#    "tournament/bots/available/hungrybot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/directedalwaysbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 1349053772 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2527845333 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2147595558 \
#    "tournament/bots/available/hungrybot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 4207771364 \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/hungrybot/run.sh" \

#./halite -d "35 35" -s 2208044740 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 2377537566 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "45 45" -s 1315306766 \
#    "tournament/bots/available/directedalwaysbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \

#./halite -d "35 35" -s 285533891 \
#    "tournament/bots/available/orientationbot/run.sh" \
#    "java -XX:+PrintGCDetails -Xloggc:gc.log -Xmx250m -jar kotlin/build/install/MyBot/MyBot.jar" \
#    "tournament/bots/available/directedalwaysbot/run.sh" \

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

# can't win?
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
