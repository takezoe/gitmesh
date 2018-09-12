FROM jetty:9.4.1-alpine

MAINTAINER Naoki Takezoe <takezoe [at] gmail.com>

ADD ./ROOT.war /var/lib/jetty/webapps/ROOT.war

EXPOSE 8080

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,stats