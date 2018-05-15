name := "gitmesh-repository-server"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.eclipse.jgit"           % "org.eclipse.jgit.http.server" % "4.9.2.201712150930-r",
  "org.eclipse.jgit"           % "org.eclipse.jgit.archive"     % "4.9.2.201712150930-r",
  "org.http4s"                %% "http4s-servlet"               % "0.18.11",
  "org.http4s"                %% "http4s-circe"                 % "0.18.11",
  "org.http4s"                %% "http4s-dsl"                   % "0.18.11",
  "org.http4s"                %% "http4s-dsl"                   % "0.18.11",
  "org.http4s"                %% "http4s-blaze-client"          % "0.18.11",
  "io.monix"                  %% "monix"                        % "2.3.3",
  "io.circe"                  %% "circe-generic"                % "0.9.3",
  "com.typesafe"               % "config"                       % "1.3.2",
  "commons-io"                 % "commons-io"                   % "2.6",
  "ch.qos.logback"             % "logback-classic"              % "1.2.3",
  "com.zaxxer"                 % "HikariCP"                     % "2.7.4",
  "org.eclipse.jetty"          % "jetty-webapp"                 % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          % "jetty-plus"                   % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          % "jetty-annotations"            % "9.4.7.v20170914" % "container",
  "javax.servlet"              % "javax.servlet-api"            % "3.1.0"           % "provided",
  "junit"                      % "junit"                        % "4.12"            % "test",
  "org.mockito"                % "mockito-core"                 % "2.13.0"          % "test"
)

enablePlugins(JettyPlugin)

containerPort := 8082