package explicitdeps

object InvalidDependencyScopesException extends sbt.FeedbackProvidedException {
  override def toString: String = "Failing the build because libraryDependencies is set in a configuration or task scope, where sbt ignores it"
}
