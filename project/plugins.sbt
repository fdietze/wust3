// scala-js
addSbtPlugin("org.scala-js"                % "sbt-scalajs"              % "1.5.1")
addSbtPlugin("ch.epfl.scala"               % "sbt-scalajs-bundler"      % "0.20.0")
addSbtPlugin("org.portable-scala"          % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scalablytyped.converter" % "sbt-converter"            % "1.0.0-beta31")

// filter locales/tz for reducing file-size of scala-java-time
addSbtPlugin("io.github.cquiroz" % "sbt-locales" % "2.4.0")
/* addSbtPlugin("io.github.cquiroz" % "sbt-tzdb"    % "0.3.1") */

// fast restart
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

/* addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.19") */
