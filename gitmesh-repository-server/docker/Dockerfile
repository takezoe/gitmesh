FROM jetty:9.4.1-alpine

MAINTAINER Naoki Takezoe <takezoe [at] gmail.com>

ADD ./ROOT.war /var/lib/jetty/webapps/ROOT.war

RUN ln -s /repos /root/repos

ENV gitmesh.url=http://localhost:8082
ENV gitmesh.directory=/root/repos
ENV gitmesh.controllerUrl=http://localhost:8081

EXPOSE 8080

VOLUME /repos

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,stats