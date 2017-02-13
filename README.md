# [nmalaguti's Halite Bots](https://halite.io/user.php?userID=2697)

## Code Organization

I started out with a few different programming languages. As you go through the commit history you'll see different top level directories for different languages.

Notably:

 - `javascript` contains many of the bots in my [tutorial](http://forums.halite.io/t/so-youve-improved-the-random-bot-now-what/482?u=nmalaguti)
 - `python` early attempts at bots, eventually used as a way to [bootstrap other languages](http://forums.halite.io/t/how-to-submit-a-bot-in-any-language/423?u=nmalaguti)
 - `java` basically just a simple bot before I switched to Kotlin
 - `kotlin` the majority of my bots
 
There are also some other projects that started as subdirectories. Some were eventually split into their own standalone community tools.

Notably:

 - `halite-lite` became [mini-halite](https://github.com/nmalaguti/mini-halite)
 - `aws` was the source for [CloudBots](http://forums.halite.io/t/cloudbots-compete-on-demand-against-some-of-my-bots/725?u=nmalaguti)
 - `tournament` was a script I wrote to run local tournaments between my bots. I eventually wrote [mini-halite](https://github.com/nmalaguti/mini-halite) as an alternative. It also contains some python code for quick iteration on ideas and a script to upload bots to a server of mine that was always running matches.
 
## Building The Kotlin Bot

You'll need a Java 8+ JDK installed. I use [Gradle](https://gradle.org) to compile and package my bot.

To build a `zip` for upload: 

```bash
$ cd kotlin
$ ./gradlew cleanDistZip distZip
```

This will make a `MyBot.zip` file in `kotlin/build/distributions/`.

To build a `jar` that can be used directly:

```bash
$ cd kotlin
$ ./gradlew installDist
```

This will make a `MyBot.jar` file in `kotlin/build/install/MyBot/`.

To run the `jar`: `java -jar MyBot.jar`

## Evolution Of My Bots

### From [BetterBot](https://github.com/nmalaguti/halite-bots/blob/690a03a/kotlin/src/main/kotlin/com/nmalaguti/halite/KotlinBot.kt) to [FrugalBot](https://github.com/nmalaguti/halite-bots/blob/3e4aa6c/kotlin/src/main/kotlin/com/nmalaguti/halite/MyBot.kt)

These bots are my original inspiration for the ["So you've Improved the Random Bot. Now what?" tutorial](http://forums.halite.io/t/so-youve-improved-the-random-bot-now-what/482?u=nmalaguti).

The tutorial basically chronicles my approach to building them. Notable differences were that I was using `strength / (production * production)` instead of just `strength / production` that I switched to later. I thought that production would be more valuable over time so it should be more heavily weighted, but it turns out that the straight ratio worked out better.

These bots had some improvements over OverkillBot:

 - Joint moves so that 2 pieces next to an environment piece could capture it sooner
 - Abandon moves so that a piece would move somewhere else if its objective would take too long to capture
 - Assist moves so that a piece would help a neighbor capture a nearby piece
 - Wastage prevention to avoid combining pieces above the 255 cap - this was super hacky and did not work great
 

I also played around with [A*](https://en.wikipedia.org/wiki/A*_search_algorithm) and other ways to try to get the "pathing" or "tunneling" behavior that [djma](https://halite.io/user.php?userID=1017) has. None of them were very effective and although the code is there, it went mostly unused in my submitted bots.

### Crossing the Chasm: [LazyBot](https://github.com/nmalaguti/halite-bots/blob/b076cfa/kotlin/src/main/kotlin/com/nmalaguti/halite/MyBot.kt)

I spent what felt like an eternity (but was more like 4 days) trying to move away from blob-like OverkillBot behavior and switch to something that could tunnel.

I eventually landed on building a BFS grid where I assigned a value to every border cell and then walked the BFS grid inward increasing each cells value by 1 each time. This worked much better than FrugalBot and was the starting point for the rest of my bots.

### From DoubleBot to ThugBot



### Final Ranking Of My Bots

| Bot                      | Mu     | Sigma | Score  | Games |
|--------------------------|--------|-------|--------|-------|
| disallowedshovebot       | 31.848 | 0.704 | 29.735 | 643   |
| disallowedshoveprodbot   | 30.833 | 0.703 | 28.724 | 622   |
| parametermixbot          | 30.815 | 0.701 | 28.713 | 2008  |
| claimcombatbot           | 30.738 | 0.683 | 28.689 | 1746  |
| waittoattackbot          | 30.480 | 0.684 | 28.428 | 2086  |
| sharpfocusbot            | 30.451 | 0.687 | 28.389 | 4598  |
| sharpparametermixbot     | 30.218 | 0.680 | 28.178 | 1666  |
| variabledirwalkbot       | 30.209 | 0.683 | 28.161 | 2079  |
| shovecrossnotclaimbot    | 30.205 | 0.692 | 28.130 | 996   |
| parametermixwaitmorebot  | 29.844 | 0.732 | 27.650 | 119   |
| shoveblackoutbot         | 29.672 | 0.691 | 27.599 | 1148  |
| punchthroughbot          | 29.404 | 0.675 | 27.380 | 1547  |
| sharpparametermix2bot    | 29.398 | 0.683 | 27.350 | 1632  |
| minvariabledirwalkbot    | 29.183 | 0.672 | 27.167 | 2116  |
| focusbot                 | 29.068 | 0.682 | 27.023 | 4901  |
| purenapbot               | 28.926 | 0.677 | 26.893 | 5248  |
| contactsquaredbot        | 28.871 | 0.672 | 26.856 | 3707  |
| inbetweensquaredbot      | 28.841 | 0.683 | 26.792 | 2084  |
| boxofrocksblackoutbot    | 28.732 | 0.683 | 26.682 | 462   |
| shovestrict64mixbot      | 28.775 | 0.703 | 26.667 | 207   |
| shovestrict64bot         | 28.794 | 0.711 | 26.661 | 192   |
| finebattlebotv2-nobruise | 28.501 | 0.684 | 26.450 | 6702  |
| napbot                   | 28.395 | 0.680 | 26.354 | 5076  |
| shovecrossbot            | 28.281 | 0.687 | 26.220 | 1000  |
| shovebetterbattlebot     | 28.220 | 0.684 | 26.169 | 1098  |
| krakenbot                | 28.212 | 0.682 | 26.167 | 4632  |
| justsquaredbot           | 28.178 | 0.684 | 26.126 | 3470  |
| boxofrocksequalbot       | 28.246 | 0.710 | 26.116 | 158   |
| thugclassicduobot        | 28.137 | 0.682 | 26.090 | 1234  |
| thugclassicpunchbot      | 27.943 | 0.687 | 25.882 | 1092  |
| finebattlebotv2          | 27.910 | 0.688 | 25.846 | 6653  |
| shoveoldstylebot         | 27.944 | 0.713 | 25.805 | 146   |
| thugclassicbot           | 27.803 | 0.682 | 25.757 | 1188  |
| spaghettistillbot        | 27.785 | 0.703 | 25.675 | 594   |
| shovebot                 | 27.675 | 0.692 | 25.600 | 161   |
| spaghettibot             | 27.478 | 0.727 | 25.298 | 114   |
| superhungrybot           | 27.313 | 0.689 | 25.244 | 5816  |
| idlestrengthbotv8        | 27.261 | 0.695 | 25.175 | 6704  |
| canidobetterbotv19       | 27.173 | 0.683 | 25.123 | 6952  |
| purestrengthbot          | 27.124 | 0.682 | 25.079 | 5158  |
| thugclassicanyduobot     | 26.883 | 0.690 | 24.813 | 1196  |
| compressbugfixbot        | 26.808 | 0.685 | 24.754 | 5229  |
| lastgaspbot              | 27.174 | 0.833 | 24.677 | 66    |
| spartanbot               | 26.639 | 0.682 | 24.594 | 5287  |
| shovefinerbattlebot      | 26.675 | 0.734 | 24.474 | 118   |
| lastgasptoobot           | 27.123 | 0.887 | 24.463 | 59    |
| compressbot              | 26.484 | 0.686 | 24.425 | 5411  |
| hungrybot                | 26.456 | 0.679 | 24.420 | 4920  |
| orientationbot           | 25.625 | 0.678 | 23.590 | 4732  |
| fighterbot               | 25.011 | 0.691 | 22.938 | 4000  |
| shoveblackborderbot      | 25.596 | 0.906 | 22.879 | 52    |
| boxofrocksbot            | 26.546 | 1.266 | 22.747 | 23    |
| thugbot                  | 23.086 | 0.702 | 20.979 | 3544  |
| blacklistbot             | 21.453 | 0.735 | 19.249 | 3648  |
| doublebot                | 20.607 | 0.738 | 18.392 | 3844  |
| swarmbot                 | 18.528 | 0.766 | 16.230 | 3327  |

## Bot Development Process




## Final Bot Code In Detail
