FROM jetty:9.4.1-alpine

MAINTAINER Naoki Takezoe <takezoe [at] gmail.com>

ADD ./ROOT.war /var/lib/jetty/webapps/ROOT.war

ENV gitmesh.url=http://localhost:8081
ENV gitmesh.replica=2
ENV gitmesh.maxDiskUsage=0.9
ENV gitmesh.database.url=jdbc:mysql://db/gitmesh?useUnicode=true&characterEncoding=utf8
ENV gitmesh.database.user=gitmesh
ENV gitmesh.database.password=gitmesh

EXPOSE 8080

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,stats