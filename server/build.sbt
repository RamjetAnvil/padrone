organization  := "com.ramjetanvil"

version       := "0.1-SNAPSHOT"

scalaVersion  := "2.12.0"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-encoding", "utf8",
  "-Xmax-classfile-name", "100",
  "-language", "higherKinds")

mainClass in assembly := Some("com.ramjetanvil.padrone.Main")

libraryDependencies ++= {
  object Version {
    val Akka = "2.4.14"
    val AkkaHttp = "10.0.0"
    val Monocle = "1.3.2"
  }

  Seq(
    "com.typesafe.akka"           %% "akka-actor"                         % Version.Akka,
    "com.typesafe.akka"           %% "akka-http-core"                     % Version.AkkaHttp,
    "com.typesafe.akka"           %% "akka-http"                          % Version.AkkaHttp,
    "com.typesafe.akka"           %% "akka-http-spray-json"               % Version.AkkaHttp,
    //"com.typesafe.akka"           %% "akka-http-testkit"                  % Version.AkkaHttp % test,

    "com.github.julien-truffaut"  %% "monocle-core"                       % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-generic"                    % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-macro"                      % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-state"                      % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-refined"                    % Version.Monocle,

    "com.github.cb372"            %% "scalacache-caffeine"                % "0.9.3",
    "io.reactivex"                %% "rxscala"                            % "0.26.4",
    "org.scala-stm"               %% "scala-stm"                          % "0.8",
    "com.jsuereth"                %% "scala-arm"                          % "2.0",
    "org.scala-lang.modules"      %% "scala-async"                        % "0.9.6",
    //"com.typesafe.play"           %% "play-json"                          % "2.5.10",
    "org.quartz-scheduler"        %  "quartz"                             % "2.2.2",
    "io.reactivex"                %  "rxjava-reactive-streams"            % "1.1.0",
    "com.maxmind.geoip2"          %  "geoip2"                             % "2.7.0",
    "com.google.guava"            %  "guava"                              % "19.0",
    "org.mindrot"                 %  "jbcrypt"                            % "0.3m",

    // Logging
    "ch.qos.logback"              %  "logback-classic"                    % "1.1.7",
    "com.typesafe.scala-logging"  %% "scala-logging"                      % "3.5.0",

    // Testing
    "org.scalatest"               %%  "scalatest"                         % "3.0.1" % "test")
}
