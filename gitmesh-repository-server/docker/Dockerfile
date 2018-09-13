FROM jetty:9.4.1-alpine

MAINTAINER Naoki Takezoe <takezoe [at] gmail.com>

ADD ./ROOT.war /var/lib/jetty/webapps/ROOT.war

RUN ln -s /repos /root/repos

ENV gitmesh.url
ENV gitmesh.directory=/root/repos
ENV gitmesh.controllerUrl

EXPOSE 8080

VOLUME /repos

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,stats