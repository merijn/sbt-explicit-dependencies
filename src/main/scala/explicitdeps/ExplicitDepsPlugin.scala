package explicitdeps

import sbt.Keys._
import sbt.{ScalaVersion => _, _}

object ExplicitDepsPlugin extends AutoPlugin {

  trait Implicits {
    implicit val moduleFilterRemoveValue: Remove.Value[ModuleFilter, ModuleFilter] =
      new Remove.Value[ModuleFilter, ModuleFilter] {
        override def removeValue(a: ModuleFilter, b: ModuleFilter): ModuleFilter = a - b
      }
  }

  object autoImport extends Implicits {
    val undeclaredCompileDependencies = taskKey[Set[Dependency]]("find all libraries that this project's code directly depends on for compilation, but which are not declared in libraryDependencies")
    val undeclaredCompileDependenciesTest = taskKey[Unit]("fail the build if there are any libraries that have not been explicitly declared as compile-time dependencies")
    val undeclaredCompileDependenciesFilter = settingKey[ModuleFilter]("Filter to specify the undeclared dependencies that you care about")

    val unusedCompileDependencies = taskKey[Set[Dependency]]("find all libraries declared in libraryDependencies that this project's code does not actually depend on for compilation")
    val unusedCompileDependenciesAggregate = taskKey[Set[Dependency]]("find all libraries declared in libraryDependencies that this project's code does not actually depend on for compilation, aggregated for all subprojects")
    val unusedCompileDependenciesTest = taskKey[Unit]("fail the build if there are any libraries declared in libraryDependencies that this project's code does not actually depend on for compilation")
    val unusedCompileDependenciesAggregateTest = taskKey[Unit]("fail the build if there are any libraries declared in libraryDependencies that this project's code does not actually depend on for compilation, aggregated for all subprojects")
    val unusedCompileDependenciesFilter = settingKey[ModuleFilter]("Filter to specify the undeclared dependencies that you care about")

    val strictDependencyScopes = settingKey[Boolean]("fail the *Test tasks if libraryDependencies is set in a configuration or task scope, where sbt ignores it (default: true)")
  }
  import autoImport._

  override def trigger = allRequirements
  override def requires = sbt.plugins.IvyPlugin
  override lazy val projectSettings = Seq(
    undeclaredCompileDependencies := undeclaredCompileDependenciesTask.value,
    undeclaredCompileDependenciesTest := undeclaredCompileDependenciesTestTask.value,
    undeclaredCompileDependenciesFilter := defaultModuleFilter,

    unusedCompileDependencies := unusedCompileDependenciesTask.value,
    unusedCompileDependenciesAggregate := unusedCompileDependenciesAggregateTask.value,
    unusedCompileDependenciesTest := unusedCompileDependenciesTestTask.value,
    unusedCompileDependenciesAggregateTest := unusedCompileDependenciesAggregateTestTask.value,
    unusedCompileDependenciesFilter := defaultModuleFilter,

    strictDependencyScopes := true,

    unusedCompileDependenciesAggregate / aggregate := false,
    unusedCompileDependenciesAggregateTest / aggregate := false
  )

  // Evaluate "csrCacheDirectory" setting which is present only in sbt 1.3.0 or newer
  private lazy val csrCacheDirectoryValueTask = Def.task {
    val extracted: Extracted = Project.extract(state.value)
    val settings = extracted.session.original
    settings.find(_.key.key.label == "csrCacheDirectory") match {
      case Some(csrCacheDirectorySetting) =>
        val csrCacheDirectoryValue = csrCacheDirectorySetting.init.evaluate(extracted.structure.data).toString
        Some(csrCacheDirectoryValue)
      case _ => None
    }
  }

  // sbt only reads libraryDependencies at project level, the configuration of a dependency is part of its
  // ModuleID (e.g. `"org" %% "name" % "1.0" % Test`). Setting `Test / libraryDependencies` therefore has no
  // effect on any classpath, which is almost certainly not what the build author intended.
  // Warns about such scopes and returns the ones that should fail the build, which is none when the build opted out.
  private lazy val reportInvalidDependencyScopesTask = Def.task {
    val log = streams.value.log
    val projectRef = thisProjectRef.value
    val structure = Project.extract(state.value).structure

    val scopes = structure.data.data.collect {
      case (scope, attributes)
          if scope.project == Select(projectRef) &&
            (scope.config != Zero || scope.task != Zero) &&
            attributes.contains(libraryDependencies.key) =>
        (scope.config.toOption.map(_.name).toList ++ scope.task.toOption.map(_.label).toList :+ libraryDependencies.key.label)
          .mkString(" / ")
    }.toSet

    Logic.warnAboutInvalidDependencyScopes(name.value, scopes, log)
    if (strictDependencyScopes.value) scopes else Set.empty[String]
  }

  private lazy val collectLibraryDepsProjectTask = Def.task {
    val log = streams.value.log

    val compileAnalysis = (Compile / compile).value.asInstanceOf[Analysis]
    val testAnalysis = (Test / compile).value.asInstanceOf[Analysis]

    val csrCacheDirectoryOpt = csrCacheDirectory.?.value.map(_.getAbsolutePath)
    // ${BASE} in sbt's virtual file ids refers to the build root, not to this subproject's directory
    val baseDir = appConfiguration.value.baseDirectory().getCanonicalFile.toPath.toString

    Logic.collectProjectDependencies(
      getAllLibraryDeps(compileAnalysis, log)(csrCacheDirectoryOpt, baseDir),
      getAllLibraryDeps(testAnalysis, log)(csrCacheDirectoryOpt, baseDir),
      libraryDependencies.value,
      ScalaVersion(scalaBinaryVersion.value, scalaVersion.value),
      log
    )
  }

  private lazy val collectLibraryDepsAllTask = Def.task {
    val log = streams.value.log

    val projectResults: Seq[ProjectDependencies] = collectLibraryDepsProjectTask.all(
      ScopeFilter(inAnyProject)
    ).value

    Logic.mergeProjectDependencies(projectResults, log)
  }

  lazy val undeclaredCompileDependenciesTask = Def.task {
    val log = streams.value.log
    val projectName = name.value
    val csrCacheDirectoryValueOpt = csrCacheDirectoryValueTask.value
    val baseDirectoryValue = appConfiguration.value.baseDirectory().getCanonicalFile.toPath.toString
    val allLibraryDeps = getAllLibraryDeps((Compile / compile).value.asInstanceOf[Analysis], log)(csrCacheDirectoryValueOpt, baseDirectoryValue)
    val libraryDeps = libraryDependencies.value
    val scalaBinaryVer = scalaBinaryVersion.value
    val scalaFullVer = scalaVersion.value
    val filter = undeclaredCompileDependenciesFilter.value

    Logic.getUndeclaredCompileDependencies(
      projectName,
      allLibraryDeps,
      libraryDeps,
      ScalaVersion(scalaBinaryVer, scalaFullVer),
      filter,
      log
    )
  }

  lazy val undeclaredCompileDependenciesTestTask = Def.task {
    val undeclaredCompileDeps = undeclaredCompileDependencies.value
    val invalidScopes = reportInvalidDependencyScopesTask.value
    if (undeclaredCompileDeps.nonEmpty)
      throw UndeclaredCompileDependenciesException
    if (invalidScopes.nonEmpty)
      throw InvalidDependencyScopesException
  }

  lazy val unusedCompileDependenciesTask = Def.task {
    val log = streams.value.log
    val projectName = name.value
    val csrCacheDirectoryValueOpt = csrCacheDirectoryValueTask.value
    val baseDirectoryValue = appConfiguration.value.baseDirectory().getCanonicalFile.toPath.toString
    val allLibraryDeps = getAllLibraryDeps((Compile / compile).value.asInstanceOf[Analysis], log)(csrCacheDirectoryValueOpt, baseDirectoryValue)
    val allLibraryTestDeps = getAllLibraryDeps((Test / compile).value.asInstanceOf[Analysis], log)(csrCacheDirectoryValueOpt, baseDirectoryValue)
    val libraryDeps = libraryDependencies.value
    val scalaBinaryVer = scalaBinaryVersion.value
    val scalaFullVer = scalaVersion.value
    val filter = unusedCompileDependenciesFilter.value

    Logic.getUnusedCompileDependencies(
      projectName,
      allLibraryDeps,
      allLibraryTestDeps,
      libraryDeps,
      ScalaVersion(scalaBinaryVer, scalaFullVer),
      filter,
      log
    )
  }

  lazy val unusedCompileDependenciesTestTask = Def.task {
    val unusedCompileDeps = unusedCompileDependencies.value
    val invalidScopes = reportInvalidDependencyScopesTask.value
    if (unusedCompileDeps.nonEmpty)
      throw UnusedCompileDependenciesException
    if (invalidScopes.nonEmpty)
      throw InvalidDependencyScopesException
  }

  lazy val unusedCompileDependenciesAggregateTask = Def.task {
    val log = streams.value.log
    val projectName = name.value
    val projectDependencies = collectLibraryDepsAllTask.value
    val filter = unusedCompileDependenciesFilter.value

    Logic.reportUnusedCompileDependencies(projectName, projectDependencies, filter, log)
  }

  lazy val unusedCompileDependenciesAggregateTestTask = Def.task {
    val unusedCompileDeps = unusedCompileDependenciesAggregate.value
    val invalidScopes = reportInvalidDependencyScopesTask.all(ScopeFilter(inAnyProject)).value.flatten
    if (unusedCompileDeps.nonEmpty)
      throw UnusedCompileDependenciesException
    if (invalidScopes.nonEmpty)
      throw InvalidDependencyScopesException
  }
}
