FROM node:16
RUN mkdir /opt/app
COPY *.js package.json /opt/app/
RUN cd /opt/app/ && npm install
WORKDIR /opt/app
CMD ["node", "OverkillBot.js"]
