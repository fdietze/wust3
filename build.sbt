import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

Global / onChangedBuildSource := IgnoreSourceChanges

inThisBuild(
  Seq(
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.13.5",
  ),
)

lazy val commonSettings = Seq(
  addCompilerPlugin(
    "org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full,
  ),
  resolvers ++=
    ("jitpack" at "https://jitpack.io") ::
      Nil,
  libraryDependencies ++=
    "org.scalatest" %%% "scalatest" % "3.2.0" % Test ::
      Nil,

  /* scalacOptions --= Seq("-Xfatal-warnings"), */
)

lazy val jsSettings = Seq(
  useYarn := true,
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  version in webpack := "4.46.0",
  npmDevDependencies in Compile += NpmDeps.funpack,
  npmDevDependencies in Compile ++= NpmDeps.Dev,
)

lazy val localeSettings = Seq(
  /* zonesFilter := { (z: String) => false }, */
)

val funStackVersion = "fc6a023"

lazy val webSettings = Seq(
  scalaJSUseMainModuleInitializer := true,
  /* scalaJSLinkerConfig ~= { _.withESFeatures(_.withUseECMAScript2015(false)) }, */
  requireJsDomEnv in Test := true,
  version in startWebpackDevServer := "3.11.2",
  webpackDevServerExtraArgs := Seq("--color"),
  webpackDevServerPort := 12345,
  webpackConfigFile in fastOptJS := Some(
    baseDirectory.value / "webpack.config.dev.js",
  ),
  webpackConfigFile in fullOptJS := Some(
    baseDirectory.value / "webpack.config.prod.js",
  ),
  webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(),
  libraryDependencies += "org.portable-scala" %%% "portable-scala-reflect" % "1.1.1",
)

lazy val webClient = project
  .enablePlugins(
    ScalaJSPlugin,
    ScalaJSBundlerPlugin,
    ScalablyTypedConverterPlugin,
    LocalesPlugin,
    /* TzdbPlugin, */
  )
  .in(file("web-client"))
  .settings(commonSettings, jsSettings, localeSettings, webSettings)
  .settings(
    webpackEmitSourceMaps in fullOptJS := false,
    libraryDependencies ++= Seq(
      Deps.outwatch.core.value,
      "com.github.cornerman.fun-stack-scala" %%% "fun-stack-web" % funStackVersion,
      "io.circe"                             %%% "circe-core"    % "0.13.0",
      "io.circe"                             %%% "circe-generic" % "0.13.0",
      "io.circe"                             %%% "circe-parser"  % "0.13.0",
    ),
    dependencyOverrides ++= Seq(
      "com.github.cornerman.colibri" %%% "colibri" % "706907c",
    ),
    npmDependencies in Compile ++=
      NpmDeps.tailwindForms ::
        NpmDeps.tailwindTypography ::
        ("snabbdom" -> "git://github.com/outwatch/snabbdom.git#semver:0.7.5") ::
        Nil,
    stIgnore ++=
      "@tailwindcss/forms" ::
        "@tailwindcss/typography" ::
        Nil,
  )

addCommandAlias("dev", "devInit; devWatchAll; devDestroy") // watch all
addCommandAlias("devInit", "webClient/fastOptJS::startWebpackDevServer")
addCommandAlias("devWatchAll", "~; webClient/fastOptJS::webpack")
addCommandAlias("devDestroy", "webClient/fastOptJS::stopWebpackDevServer")
