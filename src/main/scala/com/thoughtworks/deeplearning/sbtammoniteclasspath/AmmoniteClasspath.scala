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

    lazy val Ammonite = config("ammonite").extend(Compile)
    lazy val AmmoniteRuntime = config("ammonite-runtime").extend(Runtime)
    lazy val AmmoniteTest = config("ammonite-test").extend(Test)

    lazy val ammoniteVersion = settingKey[String]("Ammonite REPL Version")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    ammoniteVersion := {
      val fromEnv        = sys.env.get("AMMONITE_VERSION")
      def fromProps      = sys.props.get("ammonite.version")
      val defaultVersion =
        scalaBinaryVersion.value match {
          case "2.10" => "1.0.3"
          case _      => "2.2.0"
        }

      fromEnv
        .orElse(fromProps)
        .getOrElse(defaultVersion)
    }
  ) ++ {
    val configuration = !Each(Seq(Compile, Test, Runtime))
    val classpathKey = !Each(Seq(fullClasspath, dependencyClasspath, managedClasspath, unmanagedClasspath))

    Seq(
      exportToAmmoniteScript in classpathKey in configuration := {
        val code = {
          def ammonitePaths = List {
            q"_root_.ammonite.ops.Path(${(!Each((classpathKey in configuration).value)).data.toString})"
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
        val file = (crossTarget in configuration).value / s"${classpathKey.key.label}-${configuration.id}.sc"
        IO.write(file, code.syntax)
        file
      }
    )
  } ++ ammoniteRunSettings(Ammonite, Compile, Defaults.compileSettings) ++
    ammoniteRunSettings(AmmoniteTest, Test, Defaults.testSettings)
    //++ ammoniteRunSettings(AmmoniteRuntime, Runtime, Classpaths.configSettings)

  def runTask(
      classpath: Def.Initialize[Task[Classpath]],
      mainClassTask: Def.Initialize[Task[Option[String]]],
      scalaRun: Def.Initialize[Task[ScalaRun]],
      defaultArgs: Def.Initialize[Task[Seq[String]]]
  ): Def.Initialize[InputTask[Unit]] = {
    import Def.parserToInput
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val mainClass = mainClassTask.value getOrElse sys.error("No main class detected.")
      val userArgs = parser.parsed
      val args = if (userArgs.isEmpty) defaultArgs.value else userArgs
      val files = sbt.Attributed.data(classpath.value)
      println(s"Files: $files")
      scalaRun.value.run(mainClass, files, args, streams.value.log).get
    }
  }

  def defaultArgs(initialCommands: String): Seq[String] =
    if (initialCommands.isEmpty)
      Nil
    else
      Seq("--predef", initialCommands)

  def ammoniteRunSettings(
      ammConf: Configuration,
      conf: Configuration,
      defaultSettings: Seq[Setting[_]],
      classpathKeys: Seq[TaskKey[Classpath]] = Nil
  ) =
    inConfig(ammConf)(
      defaultSettings ++
        Classpaths.ivyBaseSettings ++
        Seq(
          configuration := conf,
          libraryDependencies += ("org.scala-lang" % "scala-reflect" % scalaVersion.value).force(),
          libraryDependencies += ("com.lihaoyi" %% "ammonite" % (ammConf / ammoniteVersion).value)
            .cross(CrossVersion.full),
          sourceGenerators += Def.task {
            val file = (ammConf / sourceManaged).value / "amm.scala"
            IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
            Seq(file)
          },
          run := runTask(
            fullClasspath,
            mainClass in run,
            runner in run,
            (initialCommands in console).map(defaultArgs)
          ).evaluated,
          // ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
          connectInput := true
        ) ++ classpathKeys.map(
        classpathKey =>
          classpathKey / run := runTask(
            classpathKey,
            mainClass in run,
            runner in run,
            (initialCommands in console).map(defaultArgs)
          ).evaluated)
    ) ++ Seq(
      libraryDependencies += ("com.lihaoyi" %% "ammonite" % ammoniteVersion.value % ammConf)
        .cross(CrossVersion.full)
    )

}
