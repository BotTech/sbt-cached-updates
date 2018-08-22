import org.scalactic._
import org.scalactic.Requirements._

scalaVersion := "2.12.6"

updateClassifiers / logLevel := Level.Debug

TaskKey[Unit]("uncachedUpdate") := {
  val report = updateClassifiers.value
  require(!report.stats.cached)
}

TaskKey[Unit]("cachedUpdate") := {
  val report = updateClassifiers.value
  require(report.stats.cached)
}
