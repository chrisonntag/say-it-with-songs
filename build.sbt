name := "say-it-with-songs"

version := "0.1"

scalaVersion := "2.13.8"

libraryDependencies ++= {
  Seq(
    "org.jsoup" % "jsoup" % "1.12.1",
    "com.lihaoyi" %% "cask" % "0.7.3",
    "com.lihaoyi" %% "requests" % "0.5.1",
    "net.debasishg" %% "redisclient" % "3.41",
  )
}

mainClass in (Compile, packageBin) := Some("scraper.Main")
enablePlugins(JettyPlugin)