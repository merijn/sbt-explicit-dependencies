ThisBuild / scalaVersion := sys.props("scala.version")

// None of the projects has an unused or undeclared dependency, so the only reason for the *Test tasks to fail
// is libraryDependencies being set in a configuration scope.
val core = project.in(file("core"))
  .settings(libraryDependencies += "org.typelevel" %% "cats-core" % "1.4.0")

val tools = project.in(file("tools"))
  .settings(Test / libraryDependencies += "org.typelevel" %% "cats-core" % "1.4.0")

// Same as tools, but opted out of failing: the scope is still warned about, but the *Test tasks pass
val lenient = project.in(file("lenient"))
  .settings(
    strictDependencyScopes := false,
    Test / libraryDependencies += "org.typelevel" %% "cats-core" % "1.4.0"
  )

val root = project.in(file("."))
  .aggregate(core, tools, lenient)
