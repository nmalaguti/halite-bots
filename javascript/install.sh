#!/bin/bash

curl -sL https://nodejs.org/dist/v7.1.0/node-v7.1.0-linux-x64.tar.xz | tar x

NODE="$(ls node-*/bin/node)"
NPM="$(ls node-*/bin/npm)"

$NODE $NPM install
