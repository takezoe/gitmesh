FROM jetty:9.4.1-alpine

MAINTAINER Naoki Takezoe <takezoe [at] gmail.com>

ARG VERSION

ADD ./target/scala-2.12/gitmesh-repository-server_2.12-$VERSION.war /var/lib/jetty/webapps/ROOT.war

RUN ln -s /repos /var/lib/jetty/repos

ENV gitmesh.url=http://localhost:8082
ENV gitmesh.directory=/var/lib/jetty/repos
ENV gitmesh.controllerUrl=http://localhost:8081

EXPOSE 8080

VOLUME /repos

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,stats