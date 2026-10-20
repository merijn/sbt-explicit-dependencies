scalaVersion := sys.props("scala.version")
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "1.4.0" % Test
)

TaskKey[Unit]("check") := {
  val unused = unusedCompileDependencies.value.map(_.name)
  if (unused != Set("cats-core"))
    sys.error(s"Expected unusedCompileDependencies to be Set(cats-core), got: $unused")
}
