val nexus = "https://oss.sonatype.org/"
val nexusSnapshots = nexus + "content/repositories/snapshots";
val nexusReleases = nexus + "service/local/staging/deploy/maven2";

organization := "com.mchange"

name := "jsonrpc-client"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.11"

crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked" /*, "-Xlog-implicits" */)

resolvers += ("releases" at nexusReleases)

resolvers += ("snapshots" at nexusSnapshots)

resolvers += ("Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases")

resolvers += ("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies += "com.mchange" %% "mlog-scala" % "0.3.10-SNAPSHOT"

libraryDependencies += "com.mchange" %% "yinyang" % "0.0.2-SNAPSHOT"

libraryDependencies += "com.mchange" %% "mchange-commons-scala" % "0.4.3-SNAPSHOT"

libraryDependencies += "org.eclipse.jetty" % "jetty-client" % "9.4.6.v20170531"

libraryDependencies += {
  CrossVersion.partialVersion(Keys.scalaVersion.value) match {
    case Some((2, 12)) => {
      "com.typesafe.play" %% "play-json" % "2.6.0-RC2"
    }
    case Some((2, 11)) => {
      "com.typesafe.play" %% "play-json" % "2.5.15"
    }
    case _ => {
      "com.typesafe.play" %% "play-json" % "2.4.9"
    }
  }
}


