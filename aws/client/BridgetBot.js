const request = require('request');

const Networking = require('./networking');

const network = new Networking('BridgetBot');

let moves = [];

network.on('map', (gameMap, id) => {
    request({
        url: 'https://k0zitn07h5.execute-api.us-east-1.amazonaws.com/prod/bots/bridget',
        method: 'POST',
        json: true,
        body: {
            id,
            gameMap,
            previousMoves: moves,
        }
    }, (error, response, body) => {
        moves = body.moves;
        network.sendMoves(moves);
    });
});
