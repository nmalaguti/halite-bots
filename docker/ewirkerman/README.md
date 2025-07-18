___Summary of bot functionality___
Ask me any questions on [discord](https://discordapp.com/channels/248237939123945493/248237939123945493) or the [forums](http://forums.halite.io/). No, really, please ask.

__Networking2.py__

Only interesting thing here is that at the start of every turn, while reading in the map, I created Moves for each tile that it shared with its neighbors by type [friends, enemies, neutrals or empties] so that site.friends, had all the friendly locations and the direction for them to get to the current tile. Mostly this became a shortcut for counting different neighbor types later.

__Hlt2.py__

At some point I decided the locations should be singletons so that I could hash them to put them in a set and check for identity more easily. (This created a big memory leak for me at one point, but I found myself needing to cache things because of the clock.) 
*Territory* was the idea storing a player's total strength, production, edge tiles (*frontier*) and the neutral tiles next to them (*fringe*).
*Trail* was the best path of strength'd neutrals from a given location out to a horizon. This was used to evaluate the tiles in my fringe and determine the value of root *claim*. I used a brainless BFS search for this but this was time costly to do for every tile. Values are a front-weighted average of the values (slanted towards lower strength tiles).

__Claim.py__

*Claims* are how I solved action at a distance. Capped *root claim* would have a value based either its best *trail*. Uncapped *claims* were generated as the *fringes* of enemy territories. Uncapped *root claim* values were balanced against each using number of adjacent enemies/neutrals, production and damage (if the enemy stayed still, which it rarely, but I accept that as a limitation) as multiplires. Because some maps are mostly higher value tiles  and some are lower, I balanced uncapped *claims* against capped *claims* by baselining an uncapped *claim* as an 80th percentile capped *claim*. The child values decay slightly (1% to 5% per generation) to give tiles a gradient to follow.

Each *claim* propagates a child *claim* to its friendly neighbors. This gives each tile the direction it would LIKE to go, but they sometimes need to stay still. Uncapped *claims* wait until 7 turns worth of production. I take the checkerboard approach to consolidate my tiles as they move towards their *root claim*. Each parent evaluates the possible combination of children and pulls the best set, taking into swapping to the biggest incoming child in the event of an overflow. Other unused children are released to go to their next best *claim*. Uncapped *claims* don't want avoid moving to off-checker tiles so there won't be a bad merge. Uncapped *claims* lock in their first few gens so they don't try to take neutral tiles when they should be saving themselves for combat.

Capped *claims* only move to a neutral when they will be able to take it on the turn they arrive. They keep doing BFS until they find a generation that will be able to take the *root* at the turn it arrives. It also does "multipull" - a *root claim* looks at the *trail* and pulls subsequent generations to that will be able to take the next ones in the *trail*. The value of propagating child *claim* is based on the value of the remaining *trail*

_Combat_

Most of this was done in the balancing of unbalanced *claims* against each other. Moving towards the tiles with the most enemies/neutrals keeps my tiles marching foward into enemies consistently. Damage factor was stronger than the production factor too so that generally I'd just attack as many squares as I could to damage their production the most. Because I used a black square/white square pattern f(modulo the turn number) for checkering, sometimes that wouldn't line up correctly with my combat zones. I had to explictly say that the second gen couldn't move (where gen 0 is the blank spot next to any enemy).

At some very low strengths, I could expectedly deal more damage to an enemy by staying still than I would moving, even at a combat zone, so I'd just stay still for those.


_Non Agression Pact_

If I had to enter into combat with an enemy, I didn't want to be the one who wasted the strenght breaking down the last wall (especially with the breach logic below) so I decided to wait for the enemy to do that. It worked great until cdurbin started doing it too - then it looked AMAZING. One of us was consistently winning out games, especially the three player games because we wouldn't break through, but our mutual opponent would at some point making it a two-on-one. Once people saw the value in this, it drastically changed the meta (and sadly, games got a bit less interesting).

_Breach Logic_

At some point it occurred to me that if I was standing next to a neutral tile when an enemy came through, I'd be free overkill damge for them, so I started checking for when an enemy was capable of coming through a neutral between us (but not if there was already an open path within 6 tiles). If he has the strength to take it next turn, I back my tile out of the way to dodge the overkill and then flood back through the gap (ex: https://halite.io/game.php?replay=ar1487028501-3449483492.hlt Turn 58 [2, 14])

__balance.py__

Originally, I had the idea that that file would just contain constants or functions used for balancing my bot. That idea crumbled over time and it was just whene certain functions lived. 

__moves.py__

Artifacts of earlier designs like a MoveFork, or moves with multiple directions. At one point, I just fed tile's desired move in and engine and resolved as many non-conflicting moves as I could until I would forcibly calculate the least waste resolution of a move and then try the queue again until all moves were resolved.

__MyBot.py__

Contains the orchestration login between different subsystems. At one point, I was timing out frequently, so I decided to use the full duration of my early game seconds to evaluate *Trails* rather than wait until I was time-crunched later. I spawned a second thread at a couple of point to churn through the nearby tiles I would need in future turns. I tossed those in a persistent heap for the given tile so I could just pull off the top of that as I needed them. If the first path was no longer valid (i.e. ran into enemy or my *territory* or had 0 strength, I just used the next. This let me get a more accurately forward looking valuation of my *fringe* and keep it for future turns.

PS - I tried switching to Cython (couldn't get it to work on windows for python 3.5 or 2.7). Tried Pypy to make it faster, code went slower.

__Tools__

I took me some time to get them working but some great tools I used:

_objgraph_ -  Python package for memory analysis. I caused a memory leak when I made my locations into singletons, because I had strong circular refs. This took me a day or three to diagnose, but once I go this working, I fixed the problem in short order.
_snakeviz_ - The output of the pstats package on a cprofile-created file was so underwhelming I needed better, and visualer too. Snakeviz is a visualizer that really laid out exactly where the pain points were. This is what helped me find that I had a memory leak in the first place because I saw inexplicable large times coming from changing object init methods (which turned out to be stop-the-world garbage collection).
