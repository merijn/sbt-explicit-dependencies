import sbt.CrossVersion.for3Use2_13

// Root/ThisBuild stays on 2.13, matching tools but not core, so the aggregate task can't
// just borrow a single project's ScalaVersion to make sense of every subproject's jars.
ThisBuild / scalaVersion := "2.13.18"

val commonDeps = Seq(
  "org.typelevel" %% "cats-core" % "2.13.0" cross for3Use2_13,
)

val core = project
  .in(file("core"))
  .settings(
    libraryDependencies ++= commonDeps ++ Seq("org.typelevel" %% "shapeless3-typeable" % "3.5.0"),
    scalaVersion := "3.7.4"
  )

val tools = project
  .in(file("tools"))
  .settings(libraryDependencies ++= commonDeps)

val root = project
  .in(file("."))
  .aggregate(core, tools)
  .settings(
    TaskKey[Unit]("check") := {
      val unused = unusedCompileDependenciesAggregate.value.map(_.name)
      if (unused != Set.empty[String])
        sys.error(s"Expected unusedCompileDependenciesAggregate to be empty, got: $unused")
    }
  )
