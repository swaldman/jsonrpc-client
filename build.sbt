val nexus = "https://oss.sonatype.org/"
val nexusSnapshots = nexus + "content/repositories/snapshots";
val nexusReleases = nexus + "service/local/staging/deploy/maven2";

organization := "com.mchange"

name := "jsonrpc-client"

version := "0.0.2"

scalaVersion := "2.11.11"

crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.4")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked" /*, "-Xlog-implicits" */)

resolvers += ("releases" at nexusReleases)

resolvers += ("snapshots" at nexusSnapshots)

resolvers += ("Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases")

resolvers += ("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies += "com.mchange" %% "mlog-scala" % "0.3.10"

libraryDependencies += "com.mchange" %% "yinyang" % "0.0.2"

libraryDependencies += "com.mchange" %% "mchange-commons-scala" % "0.4.4"

libraryDependencies += "com.mchange" %% "mchange-play-json-util" % "0.0.2"

libraryDependencies += "org.eclipse.jetty" % "jetty-client" % "9.4.7.v20170914"

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

publishTo := findPublishTo( version.value )

pomExtra := createPomExtra( name.value )

def findPublishTo( version : String ) = {
  if (version.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexusSnapshots )
  else
    Some("releases"  at nexusReleases )
}

def createPomExtra( projectName : String ) = (
    <url>https://github.com/swaldman/{projectName}</url>
    <licenses>
      <license>
        <name>GNU Lesser General Public License, Version 2.1</name>
        <url>http://www.gnu.org/licenses/lgpl-2.1.html</url>
        <distribution>repo</distribution>
      </license>
      <license>
        <name>Eclipse Public License, Version 1.0</name>
        <url>http://www.eclipse.org/org/documents/epl-v10.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:swaldman/{projectName}.git</url>
      <connection>scm:git:git@github.com:swaldman/{projectName}</connection>
    </scm>
    <developers>
      <developer>
        <id>swaldman</id>
        <name>Steve Waldman</name>
        <email>swaldman@mchange.com</email>
      </developer>
    </developers>
)


