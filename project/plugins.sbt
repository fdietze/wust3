// scala-js
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.8.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.20.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta36+44-f18e6850-SNAPSHOT")
resolvers                                 += MavenRepository("sonatype-s01-snapshots", "https://s01.oss.sonatype.org/content/repositories/snapshots")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.20")
