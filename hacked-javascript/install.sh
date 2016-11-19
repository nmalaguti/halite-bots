#!/bin/bash

cd "$(dirname "$0")"

curl -sL https://nodejs.org/dist/v7.1.0/node-v7.1.0-linux-x64.tar.gz | tar xz

NODE="$(ls node-*/bin/node)"
NPM="$(ls node-*/bin/npm)"

$NODE $NPM install
