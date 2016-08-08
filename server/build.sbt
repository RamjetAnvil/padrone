organization  := "com.ramjetanvil"

version       := "0.1-SNAPSHOT"

scalaVersion  := "2.11.8"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-encoding", "utf8",
  "-Xmax-classfile-name", "100",
  "-language", "higherKinds",
  "-Ybackend:GenBCode")

mainClass in assembly := Some("com.ramjetanvil.padrone.Main")

libraryDependencies ++= {
  object Version {
    val Akka = "2.4.8"
    val Monocle = "1.2.2"
  }

  Seq(
    "com.typesafe.akka"           %% "akka-actor"                         % Version.Akka,
    "com.typesafe.akka"           %% "akka-http-experimental"             % Version.Akka,
    "com.typesafe.akka"           %% "akka-http-spray-json-experimental"  % Version.Akka,

    "com.github.julien-truffaut"  %% "monocle-core"                       % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-generic"                    % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-macro"                      % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-state"                      % Version.Monocle,
    "com.github.julien-truffaut"  %% "monocle-refined"                    % Version.Monocle,

    "org.quartz-scheduler"        %  "quartz"                             % "2.2.2",
    "org.scala-lang.modules"      %  "scala-async_2.11"                   % "0.9.5",
    "com.jsuereth"                %% "scala-arm"                          % "1.4",
    "io.reactivex"                %% "rxscala"                            % "0.26.2",
    "io.reactivex"                %  "rxjava-reactive-streams"            % "1.1.0",
    "com.typesafe.play"           %  "play-json_2.11"                     % "2.4.6",
    "com.maxmind.geoip2"          %  "geoip2"                             % "2.7.0",
    "org.scala-stm"               %% "scala-stm"                          % "0.7",
    "com.google.guava"            %  "guava"                              % "19.0",
    "com.github.cb372"            %% "scalacache-caffeine"                % "0.9.1",
    "org.mindrot"                 %  "jbcrypt"                            % "0.3m",

    // Logging
    "ch.qos.logback"              %  "logback-classic"                    % "1.1.3",
    "com.typesafe.scala-logging"  %% "scala-logging"                      % "3.4.0",

    // Testing
    "org.scalatest"               %  "scalatest_2.11"                     % "2.2.5" % "test"
  )
}
