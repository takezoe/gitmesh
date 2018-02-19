name := "gitmesh-controller-server"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  //"org.eclipse.jgit"           % "org.eclipse.jgit.http.server" % "4.9.2.201712150930-r",
  //"org.eclipse.jgit"           % "org.eclipse.jgit.archive"     % "4.9.2.201712150930-r",
  "com.github.takezoe"        %% "resty"                        % "0.0.16",
  //"com.github.takezoe"        %% "scala-jdbc"                   % "1.0.5",
  "com.github.takezoe"        %% "tranquil"                     % "1.0.1",
  "com.zaxxer"                 % "HikariCP"                     % "2.7.4",
  "io.github.gitbucket"        % "solidbase"                    % "1.0.2",
  "org.postgresql"             % "postgresql"                   % "42.1.4",
  "org.mariadb.jdbc"           % "mariadb-java-client"          % "2.2.1",
  "commons-io"                 % "commons-io"                   % "2.6",
  "ch.qos.logback"             % "logback-classic"              % "1.2.3",
  "com.typesafe.akka"         %% "akka-actor"                   % "2.5.8",
  "com.enragedginger"         %% "akka-quartz-scheduler"        % "1.6.1-akka-2.5.x",
  "org.eclipse.jetty"          % "jetty-webapp"                 % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          % "jetty-plus"                   % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          % "jetty-annotations"            % "9.4.7.v20170914" % "container",
  "javax.servlet"              % "javax.servlet-api"            % "3.1.0"           % "provided",
  "junit"                      % "junit"                        % "4.12"            % "test",
  "org.mockito"                % "mockito-core"                 % "2.13.0"          % "test"
)

enablePlugins(JettyPlugin)

containerPort := 8081