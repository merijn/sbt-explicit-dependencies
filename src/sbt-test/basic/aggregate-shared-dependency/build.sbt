ThisBuild / scalaVersion := sys.props("scala.version")

val commonDeps = Seq(
  "org.typelevel" %% "cats-core" % "1.4.0"
)

val core = project.in(file("core"))
  .settings(libraryDependencies ++= commonDeps)

val tools = project.in(file("tools"))
  .settings(libraryDependencies ++= commonDeps)

val root = project.in(file("."))
  .aggregate(core, tools)
  .settings(
    TaskKey[Unit]("check") := {
      val unused = unusedCompileDependenciesAggregate.value.map(_.name)
      if (unused != Set.empty[String])
        sys.error(s"Expected unusedCompileDependenciesAggregate to be empty, got: $unused")
    }
  )
