scalaVersion := 2.12.6

TaskKey[Unit]("check") := {
  val example = exampleTask.value
  streams.value.log.info(example)
}
