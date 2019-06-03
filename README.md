# sbt-cached-updates

[![Build Status][Build Status]](https://travis-ci.org/BotTech/sbt-cached-updates)
[![Download][Download]](https://bintray.com/bottech/sbt-plugins/sbt-cached-updates/_latestVersion)

sbt-cached-updates is an sbt plugin which caches all the sbt update tasks.

By default, sbt only caches the `update` task. It does not cache `updateClassifiers` or `updateSbtClassifiers`.
This plugin fixes that.

## Diagnosis

If you notice that updates are slow or perhaps importing/refreshing the sbt project in Intellij IDEA takes forever then you may benefit from using this plugin.

To diagnose what task is slow then either:
* run sbt with:
  ```bash
  sbt -Dsbt.task.timings=true -Dsbt.task.timings.on.shutdown=false
  ```
  or,
* enable the [sbt-optimizer][sbt-optimizer] plugin.

Keep in mind that updates are forced when you run the task directly.

## Usage

This is an `AutoPlugin` which is automatically added to any project which `requires` the `IvyPlugin` which is the
default for all projects.

This plugin requires sbt 1.0.0+.

### Adding the Plugin

It is recommended that you add this plugin to your `project/plugins.sbt` file:
```scala
addSbtPlugin("nz.co.bottech" % "sbt-cached-updates" % "1.0.0")
```

If you also work on other projects that do not use this plugin then add the line above to
`~/.sbt/1.0/plugins/build.sbt`.

### Exclusions

One thing that may prevent the cache from working is the `~/.ivy2/exclude_classifiers` file.

It seems that this file is to try and prevent repeatedly attempting to download classifiers that do not exist.
The problem is that it frequently ends up either not excluding enough or excluding too much.

It is strongly recommended that you delete this file and the lock file before using this plugin.

## Troubleshooting

`updateClassifiers` will now print the message:
```sbtshell
Updating classifiers for xxx...
```
when it is updating, just like it does for `update`. If you don't see this then it is using the cached results.

You can find the update cache in `xxx/target/streams/$global/updateClassifiers/$global/streams/update_cache_2.12`.
This directory will contain two files:
* `inputs` - The hash of all the inputs.
* `output` - The results from running the update.

If the hash is different to the previous hash then there will be a cache miss and the update task will run and cache
the new results.

`updateSbtClassifiers` works a little bit differently. It will first update a "base" module and then get all the
modules from that and then again with the `classifiersModule` that has all the modules as dependencies.
The messages for this look like:
```sbtshell
Updating classifiers for sbt (base)...
...
Updating classifiers for sbt (modules)...
```

### Why do I keep seeing the "Updating classifiers" message?

This is normal if you have actually changed a dependency or if you are running the task directly.

To see what is going on enable debug logging:
```sbtshell
set xxx/updateClassifiers/logLevel := Level.Debug
```

Now when you run your task watch out for the messages:
```sbtshell
[debug] Input hash: Success(1021736120)
[debug] ...
[info] Updating classifiers for xxx...
```

The messages in between will tell you why it isn't using the cached output.

### Why has the hash/inputs changed?

You may see that the hash is changing between executions, followed by a message saying that the inputs have changed,
even though you have not changed any dependencies.

When run with debug logging there will be an additional file in the cache directory:
* `inputs.json` - JSON representation of the inputs that is hashed.

Compare the `inputs.json` files from two separate executions.

#### The classifiers change from sources to javadoc or vice versa

As mentioned earlier, sbt will generate a `~/.ivy2/exclude_classifiers` file. These are filtered out of the inputs.
Then when the update runs it will take the excludes from the update report and overwrite the `exclude_classifiers` file.

What can happen is that the `exclude_classifiers` file gets into a strange state where it has only one classifier
excluded for a library when it should have both. This causes the inputs to alternate.

For example; If `exclude_classifiers` contains `sources` then when the update runs it ignores `sources` but then the
report will say to exclude `javadoc`. Now `exclude_classifiers` will contain `javadoc` but not `sources`. It keeps
alternating between `javadoc` and `sources`.

This is likely a bug in sbt, specifically `sbt.Classpaths$.withExcludes`, but I have been unable to find a way to
reliably reproduce it.

## Credits

This plugin was generated from the [BotTech/sbt-autoplugin.g8][sbt-autoplugin] Giter8 template.

Special thanks to:
* [GitHub][Github] for hosting the git repository.
* [Travis CI][Travis CI] for running the build.
* [JFrog][JFrog] for distributing the releases on Bintray.
* [Lightbend][Lightbend] for distributing the plugin in the community sbt repository.
* All the other OSS contributors who made this project possible.

[Build Status]: https://travis-ci.org/BotTech/sbt-cached-updates.svg?branch=master
[Download]: https://api.bintray.com/packages/bottech/sbt-plugins/sbt-cached-updates/images/download.svg
[Github]: https://github.com
[JFrog]: https://jfrog.com
[Lightbend]: https://www.lightbend.com
[sbt-autoplugin]: https://github.com/BotTech/sbt-autoplugin.g8
[sbt-optimizer]: https://github.com/jrudolph/sbt-optimizer
[Travis CI]: https://travis-ci.org
