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
      "Export classpath as a .sc file, which can be loaded by another ammonite script or a Jupyter Scala notebook")
    val launchAmmoniteRepl = taskKey[Unit](
      "Launch Ammonite REPL with classpath"
    )
  }
  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = {
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
      },
      launchAmmoniteRepl in classpathKey in configuration := {
        val file = (exportToAmmoniteScript in classpathKey in configuration).value
        import java.lang.{Process, ProcessBuilder}

        System.out.synchronized {
          println("Launching Ammonite REPL...");
          new ProcessBuilder("amm", "--predef", file.toString)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .start()
            // wait for termination.
            .waitFor()
        }
      }
    )
  }
}
