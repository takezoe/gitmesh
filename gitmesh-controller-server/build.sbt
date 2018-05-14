name := "gitmesh-controller-server"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.http4s"                %% "http4s-servlet"               % "0.18.11",
  "org.http4s"                %% "http4s-circe"                 % "0.18.11",
  "org.http4s"                %% "http4s-dsl"                   % "0.18.11",
  "org.http4s"                %% "http4s-dsl"                   % "0.18.11",
  "org.http4s"                %% "http4s-blaze-client"          % "0.18.11",
  "org.tpolecat"              %% "doobie-core"                  % "0.5.2",
  "org.tpolecat"              %% "doobie-hikari"                % "0.5.2",
  "io.monix"                  %% "monix"                        % "2.3.3",
  "io.circe"                  %% "circe-generic"                % "0.9.3",
  "com.typesafe"               % "config"                       % "1.3.2",
  "com.squareup.okhttp3"       % "okhttp"                       % "3.9.1",
  "com.zaxxer"                 % "HikariCP"                     % "2.7.4",
  "io.github.gitbucket"        % "solidbase"                    % "1.0.2",
  "org.postgresql"             % "postgresql"                   % "42.1.4",
  "org.mariadb.jdbc"           % "mariadb-java-client"          % "2.2.1",
  "commons-io"                 % "commons-io"                   % "2.6",
  "ch.qos.logback"             % "logback-classic"              % "1.2.3",
  "org.eclipse.jetty"          % "jetty-webapp"                 % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          % "jetty-plus"                   % "9.4.7.v20170914" % "container",
  "org.eclipse.jetty"          % "jetty-annotations"            % "9.4.7.v20170914" % "container",
  "javax.servlet"              % "javax.servlet-api"            % "3.1.0"           % "provided",
  "junit"                      % "junit"                        % "4.12"            % "test",
  "org.mockito"                % "mockito-core"                 % "2.13.0"          % "test"
)

enablePlugins(JettyPlugin)
//addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

containerPort := 8081