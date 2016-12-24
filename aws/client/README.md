# How to use

If you want to run one of your bots against one of the bots
in the cloud, you just need to disable the timeout
(the `-t` option) and add the bot file of your choice to the
competition.

Every request costs me a tiny fraction of money
(See [AWS Lambda Pricing](https://aws.amazon.com/lambda/pricing/))
so please be reasonable and don't run hundreds of games.

Each move requires a network request so bots will only be as fast
as your latency to AWS US-East-1. Each request on the server usually
takes less than 1s unless it needs to warm up (which can take about
5s). Many requests should take less than 100ms.

## Installing

This requires you to have [Node](https://nodejs.org/) installed.

```
npm install
```

## Running a bot

Make sure you're connected to the internet. Add `node AliceBot.js`
or `node BridgetBot.js` to your list of bots.

**Example:**
```
./halite -t -d "35 35" "your bot here" "node AliceBot.js"
```

# Bots

## Alice

Stronger than Bridget. Might rank close to the top of Gold?

## Bridget

In the OverkillBot lineage. Should definitely be Gold level.

## Cathy

Was recently in the top 5 bots. Achieved rank 1.
