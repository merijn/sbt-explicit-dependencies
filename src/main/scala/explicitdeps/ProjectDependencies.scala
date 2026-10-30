package explicitdeps

/**
 * The dependencies a project (or a set of merged projects) uses and declares, already converted
 * to [[Dependency]] using the Scala version of the project they came from. Because the conversion
 * is already done, values from subprojects on different Scala versions can safely be merged.
 */
final case class ProjectDependencies(
  usedCompile: Set[Dependency],
  usedTest: Set[Dependency],
  declaredCompile: Set[Dependency],
  declaredTest: Set[Dependency]
) {
  def used: Set[Dependency] = usedCompile ++ usedTest
  def declared: Set[Dependency] = declaredCompile ++ declaredTest
}

object ProjectDependencies {
  def merge(all: Seq[ProjectDependencies]): ProjectDependencies =
    ProjectDependencies(
      all.flatMap(_.usedCompile).toSet,
      all.flatMap(_.usedTest).toSet,
      all.flatMap(_.declaredCompile).toSet,
      all.flatMap(_.declaredTest).toSet
    )
}
