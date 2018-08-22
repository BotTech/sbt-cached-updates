name := "sbt-cached-updates"
description := """An sbt plugin which caches all the sbt update tasks."""
organization := "nz.co.bottech"
organizationName := "BotTech"
homepage := Some(url("https://github.com/BotTech/sbt-cached-updates"))
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

sbtPlugin := true

enablePlugins(ScriptedPlugin)
scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  "-Dplugin.version=" + version.value
)

publishMavenStyle := false

pgpPublicRing := file("./travis/pubring.asc")
pgpSecretRing := file("./travis/secring.asc")
pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)

bintrayOrganization := Some("bottech")
bintrayPackageLabels := Seq("sbt", "plugin")

ghreleaseRepoOrg := organizationName.value
ghreleaseAssets := PgpKeys.signedArtifacts.value.values.toSeq
