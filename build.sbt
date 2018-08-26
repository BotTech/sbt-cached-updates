name := "sbt-cached-updates"
description := """An sbt plugin which caches all the sbt update tasks."""
organization := "nz.co.bottech"
organizationName := "BotTech"
homepage := Some(url("https://github.com/BotTech/sbt-cached-updates"))
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

scalaVersion := "2.12.6"

sbtPlugin := true

enablePlugins(ScriptedPlugin)
scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  "-Dplugin.version=" + version.value
)

publishMavenStyle := false

useGpg := true
pgpSecretRing := file("./travis/secring.bin")
usePgpKeyHex("D8534E92BA220A48D672800D9FF86654B58E9A01")
pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)
publish / packagedArtifacts := PgpKeys.signedArtifacts.value

bintrayOrganization := Some("bottech")
bintrayPackageLabels := Seq("sbt", "plugin")

ghreleaseRepoOrg := organizationName.value
ghreleaseAssets := (publish / packagedArtifacts).value.values.toSeq
