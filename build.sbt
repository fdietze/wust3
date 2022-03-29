Global / onChangedBuildSource := IgnoreSourceChanges // not working well with webpack devserver

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.1"

val versions = new {
  val funPack  = "0.1.4"
  val circe    = "0.14.1"
  val outwatch = "1.0.0-RC6+2-6728c9c7-SNAPSHOT"
  val colibri  = "0.2.6"
}

ThisBuild / resolvers ++= Seq(
  "jitpack" at "https://jitpack.io",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Snapshots S01" at "https://s01.oss.sonatype.org/content/repositories/snapshots", // https://central.sonatype.org/news/20210223_new-users-on-s01/
)

lazy val commonSettings = Seq(
  /* addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full), */
  scalacOptions --= Seq("-Xfatal-warnings"), // overwrite option from https://github.com/DavidGregory084/sbt-tpolecat
)

lazy val jsSettings = Seq(
  webpack / version := "4.46.0",
  useYarn           := true,
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  /* libraryDependencies += "org.portable-scala" %%% "portable-scala-reflect" % "1.1.1", */
)

lazy val webapp = project
  .enablePlugins(
    ScalaJSPlugin,
    ScalaJSBundlerPlugin,
    ScalablyTypedConverterPlugin,
  )
  .settings(commonSettings, jsSettings)
  .settings(
    libraryDependencies              ++= Seq(
      "io.github.outwatch"   %%% "outwatch"       % versions.outwatch,
      "io.github.outwatch"   %%% "outwatch-util"  % versions.outwatch, // Store, Websocket, Http
      "com.github.cornerman" %%% "colibri-router" % versions.colibri,
      "io.circe"             %%% "circe-core"     % versions.circe,
      "io.circe"             %%% "circe-generic"  % versions.circe,
      "io.circe"             %%% "circe-parser"   % versions.circe,
      /* "io.circe"             %%% "circe-generic-extras" % versions.circe, */
    ),
    Compile / npmDependencies        ++= Seq(
      "snabbdom" -> "git://github.com/outwatch/snabbdom.git#semver:0.7.5", // for outwatch, workaround for: https://github.com/ScalablyTyped/Converter/issues/293
      "firebase" -> "9.6.1",
      "daisyui"  -> "2.13.3",
    ),
    stIgnore                         ++= List(
      "snabbdom", // for outwatch, workaround for: https://github.com/ScalablyTyped/Converter/issues/293
      "daisyui",
    ),
    Compile / npmDevDependencies     ++= Seq(
      "@fun-stack/fun-pack" -> versions.funPack, // sane defaults for webpack development and production, see webpack.config.*.js
      "autoprefixer"        -> "10.4.1",
      "postcss"             -> "8.4.5",
      "postcss-loader"      -> "4.3.0",
      "postcss-import"      -> "14.0.2",
      "postcss-nesting"     -> "10.1.1",
      "postcss-extend-rule" -> "3.0.0",
      "tailwindcss"         -> "3.0.10",
    ),
    scalaJSUseMainModuleInitializer   := true,
    webpackDevServerPort              := 12345,
    webpackDevServerExtraArgs         := Seq("--color"),
    startWebpackDevServer / version   := "3.11.3",
    fullOptJS / webpackEmitSourceMaps := true,
    fastOptJS / webpackBundlingMode   := BundlingMode.LibraryOnly(),
    fastOptJS / webpackConfigFile     := Some(baseDirectory.value / "webpack.config.dev.js"),
    fullOptJS / webpackConfigFile     := Some(baseDirectory.value / "webpack.config.prod.js"),
  )

addCommandAlias("prod", "fullOptJS/webpack")
addCommandAlias("dev", "devInit; devWatchAll; devDestroy")
addCommandAlias("devInit", "; webapp/fastOptJS/startWebpackDevServer")
addCommandAlias("devWatchAll", "~; webapp/fastOptJS/webpack")
addCommandAlias("devDestroy", "webapp/fastOptJS/stopWebpackDevServer")
