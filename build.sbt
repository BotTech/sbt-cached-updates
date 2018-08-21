name := "sbt-cached-updates"
description := """An sbt plugin which caches all the sbt update tasks."""
organization := "nz.co.bottech"
organizationName := "BotTech"
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

sbtPlugin := true
publishMavenStyle := false

bintrayOrganization := Some("bottech")
bintrayPackageLabels := Seq("sbt", "plugin")

initialCommands in console := "import nz.co.bottech.sbt._"

enablePlugins(ScriptedPlugin)
scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  "-Dplugin.version=" + version.value
)
