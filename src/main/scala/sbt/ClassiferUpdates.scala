package sbt

import java.util.concurrent.TimeUnit

import sbt.Keys._
import sbt.internal.librarymanagement.InternalDefaults._
import sbt.internal.librarymanagement.UpdateClassifiersUtil._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.IvyDependencyResolution
import sbt.util.{CacheStore, CacheStoreFactory}
import sjsonnew.support.murmurhash.Hasher

import scala.concurrent.duration.FiniteDuration

object ClassiferUpdates {

  //noinspection MutatorLikeMethodIsParameterless
  // Modified from sbt.Classpaths.updateTask
  def updateClassifiersTask: Def.Initialize[Task[UpdateReport]] = {
    Def.task {
      val streamsValue = streams.value
      val log = streamsValue.log
      val ivySbtValue = ivySbt.value
      val libraryManagement = new DependencyResolution(new IvyDependencyResolution(ivySbtValue))
      val out = ivySbtValue.withIvy(log)(_.getSettings.getDefaultIvyUserDir)
      val module = (classifiersModule in Keys.updateClassifiers).value
      val appConfig = appConfiguration.value
      val updateConfig = updateClassifiersConfigurationTask.value
      val srcTypes = sourceArtifactTypes.value
      val docTypes = docArtifactTypes.value
      val cacheFactory = streamsValue.cacheStoreFactory.sub(updateCacheName.value)
      val label = projectLabel.value
      val skip = (Keys.skip in Keys.updateClassifiers).value
      val shouldForce = shouldForceUpdateClassifiersTask.value
      val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
      val unresolvedWarningConfig = (unresolvedWarningConfiguration in Keys.updateClassifiers).value
      Classpaths.withExcludes(out, module.classifiers, Defaults.lock(appConfig)) { excludes =>
        updateClassifiers(
          libraryManagement,
          cacheFactory,
          label,
          GetClassifiersConfiguration(
            module,
            excludes.toVector,
            updateConfig,
            srcTypes.toVector,
            docTypes.toVector
          ),
          skip,
          shouldForce,
          depsUpdated,
          unresolvedWarningConfig,
          Vector.empty,
          log
        )
      }
    }
  }

  //noinspection MutatorLikeMethodIsParameterless
  private def updateClassifiersConfigurationTask = Def.task {
    val updateConfig = updateConfiguration.value
    updateConfig.withArtifactFilter {
      updateConfig.artifactFilter.map { af =>
        af.withInverted(!af.inverted)
      }
    }
  }

  private def projectLabel = Def.task {
    val isPlugin = sbtPlugin.value
    val thisRef = thisProjectRef.value
    val stateValue = state.value
    val extracted = Project extract stateValue
    if (isPlugin) {
      Reference.display(thisRef)
    } else {
      val label = Def.displayRelativeReference(extracted.currentRef, thisRef)
      if (label.isEmpty) {
        "current"
      } else {
        label
      }
    }
  }

  private def shouldForceUpdateClassifiersTask = Def.task {
    val streamsValue = (streams in Keys.updateClassifiers).value
    val cacheDirectory = streamsValue.cacheDirectory
    val isRoot = executionRoots.value contains resolvedScoped.value
    val updateElapsed = forceUpdatePeriod.value match {
      case None => false
      case Some(period) =>
        val fullUpdateOutput = cacheDirectory / "out"
        val now = System.currentTimeMillis
        val diff = now - IO.getModifiedTimeOrZero(fullUpdateOutput)
        val elapsedDuration = new FiniteDuration(diff, TimeUnit.MILLISECONDS)
        fullUpdateOutput.exists() && elapsedDuration > period
    }
    isRoot || updateElapsed
  }

  //noinspection MutatorLikeMethodIsParameterless
  // Modified from sbt.Classpaths#2320
  def updateSbtClassifiersTask: Def.Initialize[Task[UpdateReport]] = {
    Def.task {
      val streamsValue = streams.value
      val log = streamsValue.log
      val ivySbtValue = ivySbt.value
      val libraryManagement = dependencyResolution.value
      val out = ivySbtValue.withIvy(log)(_.getSettings.getDefaultIvyUserDir)
      val module = classifiersModule.value
      val updateConfig = updateClassifiersConfigurationTask.value
      val appConfig = appConfiguration.value
      val srcTypes = sourceArtifactTypes.value
      val docTypes = docArtifactTypes.value
      val cacheFactory = streamsValue.cacheStoreFactory.sub(updateCacheName.value)
      val label = "sbt"
      val skip = (Keys.skip in Keys.updateClassifiers).value
      val shouldForce = shouldForceUpdateClassifiersTask.value
      val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
      val unresolvedWarningConfig = (unresolvedWarningConfiguration in update).value
      Classpaths.withExcludes(out, module.classifiers, Defaults.lock(appConfig)) { excludes =>
        transitiveScratch(
          libraryManagement,
          cacheFactory,
          label,
          GetClassifiersConfiguration(
            module,
            excludes.toVector,
            updateConfig,
            srcTypes.toVector,
            docTypes.toVector
          ),
          skip,
          shouldForce,
          depsUpdated,
          unresolvedWarningConfig,
          log
        )
      }
    }
  }

  // Modified from sbt.internal.LibraryManagement.transitiveScratch
  def transitiveScratch(libraryManagement: DependencyResolution,
                        cacheStoreFactory: CacheStoreFactory,
                        label: String,
                        config: GetClassifiersConfiguration,
                        skip: Boolean,
                        force: Boolean,
                        depsUpdated: Boolean,
                        uwconfig: UnresolvedWarningConfiguration,
                        log: Logger): UpdateReport = {
    import config.{module => mod, updateConfiguration => updateConfig}
    import mod.{id, scalaModuleInfo, dependencies => deps}
    val base = restrictedCopy(id, confs = true).withName(id.name + "$" + label)
    val module = libraryManagement.moduleDescriptor(base, deps, scalaModuleInfo)
    val report = cachedUpdateClassifiers(
      libraryManagement,
      module,
      cacheStoreFactory.sub("base"),
      s"$label (base)",
      updateConfig,
      identity,
      skip,
      force,
      depsUpdated,
      uwconfig,
      log
    )
    val newConfig = config.withModule(mod.withDependencies(report.allModules))
    updateClassifiers(
      libraryManagement,
      cacheStoreFactory.sub("modules"),
      s"$label (modules)",
      newConfig,
      skip,
      force,
      depsUpdated,
      uwconfig,
      Vector.empty,
      log
    )
  }

  // Modified from sbt.librarymanagement.DependencyResolution.updateClassifiers
  def updateClassifiers(libraryManagement: DependencyResolution,
                        cacheStoreFactory: CacheStoreFactory,
                        label: String,
                        config: GetClassifiersConfiguration,
                        skip: Boolean,
                        force: Boolean,
                        depsUpdated: Boolean,
                        unresolvedWarningConfig: UnresolvedWarningConfiguration,
                        artifacts: Vector[(String, ModuleID, Artifact, File)],
                        log: Logger): UpdateReport = {
    import config.{module => mod, updateConfiguration => updateConfig, _}
    import mod.{configurations => confs, _}
    val artifactFilter = getArtifactTypeFilter(updateConfig.artifactFilter)
    assert(classifiers.nonEmpty, "classifiers cannot be empty")
    assert(artifactFilter.types.nonEmpty, "UpdateConfiguration must filter on some types")
    val baseModules = dependencies map { m =>
      restrictedCopy(m, confs = true)
    }
    // Adding list of explicit artifacts here.
    val exls = Map(excludes map { case (k, v) => (k, v.toSet) }: _*)
    val deps = baseModules.distinct flatMap classifiedArtifacts(classifiers, exls, artifacts)
    val base = restrictedCopy(id, confs = true).withName(id.name + classifiers.mkString("$", "_", ""))
    val moduleSetting = ModuleDescriptorConfiguration(base, ModuleInfo(base.name))
      .withScalaModuleInfo(scalaModuleInfo)
      .withDependencies(deps)
      .withConfigurations(confs)
    val module = libraryManagement.moduleDescriptor(moduleSetting)
    // c.copy ensures c.types is preserved too
    val upConf = updateConfig.withMissingOk(true)
    cachedUpdateClassifiers(
      libraryManagement,
      module,
      cacheStoreFactory,
      label,
      upConf,
      addMissingClassifiers(config),
      skip,
      force,
      depsUpdated,
      unresolvedWarningConfig,
      log
    )
  }

  private def addMissingClassifiers(config: GetClassifiersConfiguration)(report: UpdateReport) = {
    // The artifacts that came from Ivy don't have their classifier set, let's set it according to
    // FIXME: this is only done because IDE plugins depend on `classifier` to determine type. They
    val sourceTypes = config.sourceArtifactTypes.map(_ -> Artifact.SourceClassifier)
    val docTypes = config.docArtifactTypes.map(_ -> Artifact.DocClassifier)
    val typeClassifierMap = (sourceTypes ++ docTypes).toMap
    report.substitute { (_, _, artFileSeq) =>
      artFileSeq map {
        case (art, f) =>
          // Deduce the classifier from the type if no classifier is present already
          art.withClassifier(art.classifier orElse typeClassifierMap.get(art.`type`)) -> f
      }
    }
  }

  private type UpdateInputs = (Long, ModuleSettings, UpdateConfiguration)

  // Modified from sbt.internal.LibraryManagement.cachedUpdate
  def cachedUpdateClassifiers(libraryManagement: DependencyResolution,
                              module: ModuleDescriptor,
                              cacheStoreFactory: CacheStoreFactory,
                              label: String,
                              updateConfig: UpdateConfiguration,
                              transform: UpdateReport => UpdateReport,
                              skip: Boolean,
                              force: Boolean,
                              depsUpdated: Boolean,
                              unresolvedWarningConfig: UnresolvedWarningConfiguration,
                              log: Logger): UpdateReport = {

    /* Resolve the module settings from the inputs. */
    def resolve: UpdateReport = {
      import sbt.util.ShowLines._

      log.info(s"Updating classifiers for $label...")
      val reportOrUnresolved: Either[UnresolvedWarning, UpdateReport] =
        libraryManagement.update(module, updateConfig, unresolvedWarningConfig, log)
      val report = reportOrUnresolved match {
        case Right(report0) => report0
        case Left(unresolvedWarning) =>
          unresolvedWarning.lines.foreach(log.warn(_))
          throw unresolvedWarning.resolveException
      }
      log.info("Done updating classifiers.")
      transform(report)
    }

    /* Check if a update report is still up to date or we must resolve again. */
    def upToDate(out: UpdateReport): Boolean = {
      out.allFiles.forall(f => fileUptodate(f, out.stamps)) &&
        fileUptodate(out.cachedDescriptor, out.stamps)
    }

    /* Skip resolve if last output exists, otherwise error. */
    def skipResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      import sbt.librarymanagement.LibraryManagementCodec._
      Tracked.lastOutput[UpdateInputs, UpdateReport](cache) {
        case (_, Some(out)) => markAsCached(out)
        case _ =>
          sys.error("Skipping update requested, but update has not previously run successfully.")
      }
    }

    // Mark UpdateReport#stats as "cached." This is used by the dependers later
    // to determine whether they now need to run update in the above `upToDate`.
    def markAsCached(ur: UpdateReport): UpdateReport =
      ur.withStats(ur.stats.withCached(true))

    def doResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      val doCachedResolve = { (inChanged: Boolean, updateInputs: UpdateInputs) =>
        import sbt.librarymanagement.LibraryManagementCodec._
        log.log(Level.Debug, {
          cacheStoreFactory.make("inputs.json").write(updateInputs)
          val hash = Hasher.hash(updateInputs)
          s"Inputs hash: $hash"
        })
        val cachedResolve = Tracked.lastOutput[UpdateInputs, UpdateReport](cache) {
          case (_, None) =>
            log.debug("Update has not previously run successfully.")
            resolve
          case (_, Some(out)) =>
            if (force) {
              log.debug("Update has been forced.")
              resolve
            } else if (depsUpdated) {
              log.debug("Internal dependencies have changed.")
              resolve
            } else if (inChanged) {
              log.debug("Inputs have changed.")
              resolve
            } else if (!upToDate(out)) {
              log.debug("Output is out of date.")
              resolve
            } else {
              log.debug("Using cached output.")
              markAsCached(out)
            }
        }
        import scala.util.control.Exception.catching
        catching(classOf[NullPointerException], classOf[OutOfMemoryError])
          .withApply { t =>
            val resolvedAgain = resolve
            val culprit = t.getClass.getSimpleName
            log.warn(s"Update task caching failed due to $culprit.")
            log.warn("Report the following output to sbt:")
            resolvedAgain.toString.lines.foreach(log.warn(_))
            log.trace(t)
            resolvedAgain
          }
          .apply(cachedResolve(updateInputs))
      }
      import LibraryManagementCodec._
      Tracked.inputChanged(cacheStoreFactory.make("inputs"))(doCachedResolve)
    }

    // Get the handler to use and feed it in the inputs
    // This is lm-engine specific input hashed into Long
    val extraInputHash = module.extraInputHash
    val settings = module.moduleSettings
    val outStore = cacheStoreFactory.make("output")
    val handler = if (skip && !force) skipResolve(outStore) else doResolve(outStore)
    // Remove clock for caching purpose
    val withoutClock = updateConfig.withLogicalClock(LogicalClock.unknown)
    val inputs = (extraInputHash, settings, withoutClock)
    handler(inputs)
  }

  // Copied from sbt.internal.LibraryManagement#fileUptodate
  private def fileUptodate(file: File, stamps: Map[File, Long]): Boolean = {
    stamps.get(file).forall(_ == IO.getModifiedTimeOrZero(file))
  }
}
