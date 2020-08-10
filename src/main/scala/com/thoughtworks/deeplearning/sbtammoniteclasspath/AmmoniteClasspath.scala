package com.thoughtworks.deeplearning.sbtammoniteclasspath

import com.thoughtworks.dsl.keywords.Each
import sbt._, Keys._
import sbt.plugins.JvmPlugin
import scala.meta._

/**
  * @author 杨博 (Yang Bo)
  */
object AmmoniteClasspath extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = JvmPlugin

  object autoImport {
    val exportToAmmoniteScript = taskKey[File](
      "Export classpath as a .sc file, which can be loaded by another ammonite script or an Almond notebook")

    lazy val Ammonite = config("ammonite")
      .extend(Compile)
      .withDescription("Ammonite config to run REPL, similar to Compile (default) config.")
    lazy val AmmoniteTest =
      config("ammonite-test")
      .extend(Test)
      .withDescription("Ammonite config to run REPL, similar to Test config.")
    lazy val AmmoniteRuntime = config("ammonite-runtime")
      .extend(Runtime)
      .withDescription("Ammonite config to run REPL, similar to Runtime config.")

    lazy val ammoniteVersion = settingKey[String]("Ammonite REPL Version")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    baseSettings ++
      classpathExportSettings ++
      ammoniteRunSettings(Ammonite, Compile) ++
      ammoniteRunSettings(AmmoniteTest, Test) ++
      ammoniteRunSettings(AmmoniteRuntime, Runtime)

  def baseSettings: Seq[Def.Setting[_]] = Seq(
    ammoniteVersion := {
      scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case _      => "latest.release"
      }
    }
  )

  private val allClasspathKeys = Seq(fullClasspath, dependencyClasspath, managedClasspath, unmanagedClasspath)

  def classpathExportSettings: Seq[Def.Setting[_]] = {
    val configuration = !Each(Seq(Compile, Test, Runtime))
    val classpathKey = !Each(allClasspathKeys)

    Seq(
      configuration / classpathKey  / exportToAmmoniteScript := {
        val code = {
          def ammonitePaths = List {
            q"_root_.ammonite.ops.Path(${(!Each((configuration / classpathKey).value)).data.toString})"
          }

          def mkdirs = List {
            val ammonitePath = !Each(ammonitePaths)
            q"""
            if (!_root_.ammonite.ops.exists($ammonitePath)) {
              _root_.ammonite.ops.mkdir($ammonitePath)
            }
            """
          }

          q"""
          ..$mkdirs
          interp.load.cp(Seq(..$ammonitePaths))
          """
        }
        val file = (configuration / crossTarget).value / s"${classpathKey.key.label}-${configuration.id}.sc"
        IO.write(file, code.syntax)
        file
      }
    )
  }

  def ammoniteRunSettings(ammConf: Configuration, backingConf: Configuration): Seq[Def.Setting[_]] =
    inConfig(ammConf)(
      Defaults.compileSettings ++
        Classpaths.ivyBaseSettings ++
        Seq(
          libraryDependencies := Seq(("com.lihaoyi" %% "ammonite" % (ammConf / ammoniteVersion).value).cross(CrossVersion.full)),
          connectInput        := true,
          initialCommands     := (backingConf / console / initialCommands).value,
          run                 := runTask(ammConf, backingConf, fullClasspath, ammConf / run / runner).evaluated
        ) ++
        allClasspathKeys.map(classpathKey =>
          classpathKey / run  := runTask(ammConf, backingConf, classpathKey, ammConf / run / runner).evaluated
        )
    )

  private def runTask(
      ammConf: Configuration,
      backingConf: Configuration,
      classpath: TaskKey[Classpath],
      scalaRun: Def.Initialize[Task[ScalaRun]],
  ): Def.Initialize[InputTask[Unit]] = {
    import Def.parserToInput
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val mainClass = "ammonite.Main"
      val userArgs = parser.parsed
      val args = Seq(
        "--predef",       (backingConf / classpath / exportToAmmoniteScript).value.absolutePath,
        "--predef-code",  (ammConf / console / initialCommands).value
      ) ++ userArgs
      val ammoniteOnlyClasspathFiles = sbt.Attributed.data((ammConf / managedClasspath).value)
      scalaRun.value.run(mainClass, ammoniteOnlyClasspathFiles, args, streams.value.log).get
    }
  }

}
