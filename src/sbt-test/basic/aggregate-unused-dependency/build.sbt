ThisBuild / scalaVersion := sys.props("scala.version")

val core = project.in(file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "1.4.0",
      "org.scalaz" %% "scalaz" % "7.2.26"
    )
  )

val tools = project.in(file("tools"))
  .settings(libraryDependencies ++= Seq("org.typelevel" %% "cats-core" % "1.4.0"))

val root = project.in(file("."))
  .aggregate(core, tools)
  .settings(
    TaskKey[Unit]("check") := {
      val unused = unusedCompileDependenciesAggregate.value.map(_.name)
      if (unused != Set("scalaz"))
        sys.error(s"Expected unusedCompileDependenciesAggregate to be Set(scalaz), got: $unused")
    }
  )
