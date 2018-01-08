name := "dgit-controller-server"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  //"org.eclipse.jgit"           % "org.eclipse.jgit.http.server" % "4.9.2.201712150930-r",
  //"org.eclipse.jgit"           % "org.eclipse.jgit.archive"     % "4.9.2.201712150930-r",
  "com.github.takezoe"        %% "resty"                        % "0.0.14",
  "com.github.takezoe"        %% "scala-jdbc"                   % "1.0.5",
  "com.zaxxer"                 %  "HikariCP"                    % "2.7.4",
  "org.postgresql"             %  "postgresql"                  % "42.1.4",
  "commons-io"                 % "commons-io"                   % "2.6",
  //"org.apache.httpcomponents"  % "httpclient"                   % "4.5.4",
  //"com.h2database"             % "h2"                           % "1.4.196",
  "ch.qos.logback"             % "logback-classic"              % "1.2.3",
  "com.typesafe.akka"         %% "akka-actor"                   % "2.5.8",
  "com.enragedginger"         %% "akka-quartz-scheduler"        % "1.6.1-akka-2.5.x",
  "org.eclipse.jetty"          % "jetty-webapp"                 % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          %  "jetty-plus"                  % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          %  "jetty-annotations"           % "9.4.7.v20170914" % "container",
  "javax.servlet"              % "javax.servlet-api"            % "3.1.0"           % "provided",
  "junit"                      % "junit"                        % "4.12"            % "test",
  "org.mockito"                % "mockito-core"                 % "2.13.0"          % "test"
)

enablePlugins(JettyPlugin)