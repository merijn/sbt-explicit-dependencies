ThisBuild / scalaVersion := sys.props("scala.version")

// A file-based repository inside the build root, so that sbt (1.4+) identifies the jar by a ${BASE}-relative path.
ThisBuild / resolvers += MavenRepository("local-test-repo", ((ThisBuild / baseDirectory).value / "repo").toURI.toString)

val core = project.in(file("core"))
  .settings(
    libraryDependencies += "mylib" % "mylib" % "1.0",
    TaskKey[Unit]("checkBaseToken") := {
      val analysis = (Compile / compile).value.asInstanceOf[sbt.internal.inc.Analysis]
      val sbtIs14OrNewer = CrossVersion.partialVersion(sbtVersion.value).exists {
        case (major, minor) => major > 1 || (major == 1 && minor >= 4)
      }

      if (sbtIs14OrNewer) {
        val deps = analysis.relations.allLibraryDeps.asInstanceOf[Set[AnyRef]]
        val expected = "${BASE}/repo/mylib/mylib/1.0/mylib-1.0.jar"
        val found = deps.exists { dep =>
          dep.getClass.getSimpleName.contains("VirtualFile") &&
            dep.getClass.getMethod("id").invoke(dep).toString.replace('\\', '/') == expected
        }
        if (!found) sys.error(s"Expected compile analysis to contain $expected; got: $deps")
      }
    }
  )

val root = project.in(file("."))
  .aggregate(core)
  .settings(
    TaskKey[Unit]("check") := {
      val unused = unusedCompileDependenciesAggregate.value.map(_.name)
      if (unused != Set.empty[String])
        sys.error(s"Expected unusedCompileDependenciesAggregate to be empty, got: $unused")
    }
  )
