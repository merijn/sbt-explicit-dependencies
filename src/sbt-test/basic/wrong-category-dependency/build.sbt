scalaVersion := sys.props("scala.version")
// Declared as a plain compile dependency, but only actually used by test code below,
// so it belongs in "% Test" instead.
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "1.4.0"
)

TaskKey[Unit]("check") := {
  val unused = unusedCompileDependencies.value.map(_.name)
  if (unused != Set("cats-core"))
    sys.error(s"Expected unusedCompileDependencies to be Set(cats-core), got: $unused")
}
