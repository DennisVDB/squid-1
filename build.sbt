val paradiseVersion = "2.1.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.11.7",
  organization := "ch.epfl.data",
  autoCompilerPlugins := true,
  scalacOptions ++= Seq("-feature", "-language:implicitConversions", "-language:higherKinds", "-language:postfixOps"
    , "-deprecation"
  ),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  //addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "junit" % "junit-dep" % "4.10" % "test",
    "org.scalatest" % "scalatest_2.11" % "2.2.0" % "test"
  )
)
lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }

lazy val main = (project in file(".")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(
    // other settings here
    addCommandAlias("bench", "benchmark/run"): _*
  )

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies += scalaReflect.value,
    libraryDependencies += scalaCompiler.value,
    // other settings here
    //libraryDependencies += "ch.epfl.lamp" % "scala-yinyang_2.11" % "0.2.0-SNAPSHOT",
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-library" % _),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _),
    libraryDependencies ++= (
        if (scalaVersion.value.startsWith("2.10")) List("org.scalamacros" %% "quasiquotes" % paradiseVersion)
        else Nil
      )
  )

lazy val benchmark = (project in file("benchmark")).
  settings(commonSettings: _*).
  settings(
    
    // ScalaMeter (http://scalameter.github.io/home/gettingstarted/0.7/sbt/index.html)
    libraryDependencies ++= Seq("com.storm-enroute" %% "scalameter" % "0.7"),
    //libraryDependencies ++= Seq("com.storm-enroute" %% "scalameter" % "0.8-SNAPSHOT"),
    fork := true // otherwise runs of the compiler won't find macro definitions 
    /*,
    resultDir := ""*/
    
    //testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    //parallelExecution in Test := false
  ).
  dependsOn(main)


val SCVersion = "0.1.2-SNAPSHOT"

lazy val scBackend = (project in file("sc-backend")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq("ch.epfl.data" % "sc-pardis-compiler_2.11" % SCVersion)
  ).
  dependsOn(core)



