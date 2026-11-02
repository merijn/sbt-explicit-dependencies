ThisBuild / scalaVersion := sys.props("scala.version")

// This tests that the aggregate task is not thrown off by libraryDependencies being set in a configuration
// scope, and that it agrees with the per-project task. Dependencies in core:
// - cats-core:        used by main code
// - scalaz:           unused, the only one that may be reported
// - cats-kernel:      declared as '% Test' and used by test code
// - alleycats-core:   added to 'Test / libraryDependencies'
// The last one is a config-scoped *setting*, which sbt ignores. It must not be counted as a declared
// dependency. Validating such scopes is covered by the invalid-dependency-scope test, so it is switched off
// here to leave scalaz as the only reason for the *Test tasks to fail.
val core = project.in(file("core"))
  .settings(
    strictDependencyScopes := false,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "1.4.0",
      "org.scalaz" %% "scalaz" % "7.2.26",
      "org.typelevel" %% "cats-kernel" % "1.4.0" % Test
    ),
    Test / libraryDependencies += "org.typelevel" %% "alleycats-core" % "1.4.0"
  )

val root = project.in(file("."))
  .aggregate(core)
  .settings(
    TaskKey[Unit]("check") := {
      val aggregate = unusedCompileDependenciesAggregate.value.map(_.name)
      val perProject = (core / unusedCompileDependencies).value.map(_.name)
      if (aggregate != Set("scalaz"))
        sys.error(s"Expected unusedCompileDependenciesAggregate to be Set(scalaz), got: $aggregate")
      if (aggregate != perProject)
        sys.error(s"Aggregate result $aggregate differs from per-project result $perProject")

      // Confirms that sbt really ignores the config-scoped setting, which is why it must not count as a
      // declared dependency, while the '% Test' dependency does end up on the test classpath
      val testCp = (core / Test / dependencyClasspath).value.flatMap(_.get(moduleID.key)).map(_.name).toSet
      if (testCp.exists(_.startsWith("alleycats-core")))
        sys.error(s"Test / libraryDependencies unexpectedly reached the test classpath: $testCp")
      if (!testCp.exists(_.startsWith("cats-kernel")))
        sys.error(s"Expected '% Test' dependency cats-kernel on the test classpath, got: $testCp")
    }
  )
