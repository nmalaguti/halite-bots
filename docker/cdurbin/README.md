# Halite

The code in this repo was my final bot submission for the competition at https://halite.io. I was pleasantly surprised that my bot ended up finishing 5th in the competition out of roughly 1600 competitors.

### Summary
I started working on it in mid-November thinking this would be a great chance for me to learn more about machine learning and implement something useful. I wrote a quick bot in Java, became addicted, and started writing a bot in Clojure. I quickly abandoned my plan of trying to write a ML based bot.

Early on I implemented a ton of different interesting behaviors and kept the ones that seemed to help and threw out the ones that did not seem to work. My bot changed so much over the course of the competition and not always for the better. My final bot was a hodgepodge of organically grown and stuck together behaviors rather than some well thought out plan.

Some of the interesting behaviors I started with involved using the init time to analyze and plan my moves out based on the whole map. Unfortunately there were some major timeout issues and I abandoned those behaviors since I was timing out too much.

I never managed to find the time to resurrect those behaviors once we got to the bottom of the timeouts and some additional time was granted by the organizers. Sadly my bot stagnated the last month of the competition as I was having to work overtime at work, and was too exhausted to work on my bot after the kids went to sleep. I hope I'm rejuvenated for Halite 2.0.

### Behaviors in final bot

I think I had a lot of the same behaviors that everyone seemed to implement. Just about all of the top players subscribed to the [Non Aggression Pact](http://forums.halite.io/t/the-non-aggression-pact/724/9) of which I was an early convert.

My algorithm for choosing which sites to take generally worked out such that I maintained a good amount of strength to withstand attacks while also expanding in such a way to keep up production wise. I had several other gathering algorithms in earlier bots that were better at maximizing production on a lot of maps, but in the end I chose this one because it seemed to be the most consistent in preventing really poor decisions.

### Biggest strengths
Most of the top bots were better than mine in just about every category except one. My border management and appropriately using a ton of strength were keys to helping me compete with the best bots. My strategy was to prevent breaking through enemy borders unless I was at least twice as strong as another player. So if someone attacked me I would not go all out attacking them, I just continued to send my units through whichever border was already open, but was careful to not open any additional borders unless they were much weaker. Then when I found an opponent that had less than half my strength I would go all out crashing through all the borders to gain their territory as quickly as possible before any other bots had a chance.

There were only a couple of games where I saw this strategy backfire. Picking on the weakened bots is not exactly the noblest of strategies, but was quite effective.

1. The battles were easy to win (don't lose a lot of strength)
2. Generally enemy (non-neutral) tiles are the best ones (gain a lot of production)

### Biggest weaknesses

#### Overkill avoidance
My final bot had a feeble attempt at overkill avoidance. I naively thought I could put together an effective strategy for avoiding overkill in a couple of hours right before the end of the competition. In the end I was unable to implement something that allowed me to both avoid overkill and quickly send a lot of strength through weakened enemy territories so I opted for the latter. My bot was nowhere near a diamond level bot for 1 one 1 games as a result.

#### Two site buffer on borders
This was a bad strategy. I went with a two site buffer rather than a single one because on a few occasions I saw that two bots that avoided borders would both move to take neighboring tiles on the same turn and inadvertently open a border. I should have just added a single line of code to check to make sure it wasn't possible for an accidental border opening based on my opponents strength two tiles away.

This really killed me against the top 4 players because as we all implemented the Non-aggression pact and so they had a huge advantage in being able to:

1. Take better neutral sites
2. Grab more neutral sites
3. Cut my bot off from certain locations on the map

### Favorite games

1. [General path to victory](https://halite.io/game.php?replay=ar1487255004-2319299632.hlt)
2. [Most uncharacteristic win](https://halite.io/game.php?replay=ar1487172889-1808440371.hlt)
3. [Saddest loss](https://halite.io/game.php?replay=ar1485723663-4282149313.hlt)
4. [Proudest Achievement - first win against the top 5 players at the time](https://halite.io/game.php?replay=ar1482144148-2475020940.hlt)
