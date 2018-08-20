package nz.co.bottech.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.IvyPlugin

object CachedUpdatesPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = IvyPlugin

  override lazy val projectSettings: Seq[Def.Setting[_]] = cachedUpdatesSettings

  private lazy val cachedUpdatesSettings = {
    Seq(
      updateSbtClassifiers := (LocalRootProject / updateSbtClassifiers).value,
      updateClassifiers := (ClassiferUpdates.updateClassifiersTask tag(Tags.Update, Tags.Network)).value
    ) ++ inScope(ThisScope.in(LocalRootProject, updateSbtClassifiers.key)) {
      Defaults.TaskGlobal / updateSbtClassifiers := {
        (ClassiferUpdates.updateSbtClassifiersTask tag(Tags.Update, Tags.Network)).value
      }
    }
  }
}
