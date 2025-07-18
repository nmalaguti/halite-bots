FROM gcc
RUN mkdir /opt/app
COPY . /opt/app
WORKDIR /opt/app
RUN g++ -O2 -lm -std=c++11 -o MyBot MyBot.cpp
CMD ["./MyBot"]