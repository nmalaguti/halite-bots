FROM clojure:lein
RUN mkdir /opt/app
COPY . /opt/app
WORKDIR /opt/app
RUN lein uberjar
CMD ["java", "-jar", "/opt/app/target/uberjar/MyBot.jar"]
