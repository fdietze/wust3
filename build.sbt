import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

Global / onChangedBuildSource := IgnoreSourceChanges

inThisBuild(
  Seq(
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "3.0.0",
  ),
)

lazy val commonSettings = Seq(
  resolvers ++= Seq(("jitpack" at "https://jitpack.io")),
  libraryDependencies ++= Seq("org.scalatest" %%% "scalatest" % "3.2.9" % Test),
  scalacOptions -= "-Xfatal-warnings",
)

lazy val jsSettings = Seq(
  useYarn := true,
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  webpack / version := "4.46.0",
  Compile / npmDevDependencies += NpmDeps.funpack,
  Compile / npmDevDependencies ++= NpmDeps.Dev,
)

lazy val localeSettings = Seq(
  /* zonesFilter := { (z: String) => false }, */
)

val funStackVersion = "fc6a023"

lazy val webSettings = Seq(
  scalaJSUseMainModuleInitializer := true,
  /* scalaJSLinkerConfig ~= { _.withESFeatures(_.withUseECMAScript2015(false)) }, */
  Test / requireJsDomEnv := true,
  startWebpackDevServer / version := "3.11.2",
  webpackDevServerExtraArgs := Seq("--color"),
  webpackDevServerPort := 12345,
  fastOptJS / webpackConfigFile := Some(
    baseDirectory.value / "webpack.config.dev.js",
  ),
  fullOptJS / webpackConfigFile := Some(
    baseDirectory.value / "webpack.config.prod.js",
  ),
  fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
  /* libraryDependencies += "org.portable-scala" %%% "portable-scala-reflect" % "1.1.1", */
)

lazy val webClient = project
  .enablePlugins(
    ScalaJSPlugin,
    ScalaJSBundlerPlugin,
    ScalablyTypedConverterPlugin,
    /* LocalesPlugin, */
    /* TzdbPlugin, */
  )
  .in(file("web-client"))
  .settings(commonSettings, jsSettings, localeSettings, webSettings)
  .settings(
    fullOptJS / webpackEmitSourceMaps := false,
    libraryDependencies ++= Seq(
      (Deps.outwatch.core.value).cross(CrossVersion.for3Use2_13),
      /* "com.github.cornerman.fun-stack-scala" %%% "fun-stack-web" % funStackVersion, */
      "io.circe" %%% "circe-core"    % "0.14.1",
      "io.circe" %%% "circe-generic" % "0.14.1",
      "io.circe" %%% "circe-parser"  % "0.14.1",
    ),
    dependencyOverrides ++= Seq(
      ("com.github.cornerman.colibri" %%% "colibri" % "706907c").cross(CrossVersion.for3Use2_13),
    ),
    Compile / npmDependencies ++= Seq(
      NpmDeps.tailwindForms,
      NpmDeps.tailwindTypography,
      ("snabbdom" -> "git://github.com/outwatch/snabbdom.git#semver:0.7.5"),
    ),
    stIgnore ++= List(
      "@tailwindcss/forms",
      "@tailwindcss/typography",
    ),
  )

addCommandAlias("dev", "devInit; devWatchAll; devDestroy") // watch all
addCommandAlias("devInit", "webClient/fastOptJS/startWebpackDevServer")
addCommandAlias("devWatchAll", "~; webClient/fastOptJS/webpack")
addCommandAlias("devDestroy", "webClient/fastOptJS/stopWebpackDevServer")
