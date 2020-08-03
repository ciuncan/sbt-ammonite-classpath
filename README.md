# sbt-ammonite-classpath

**sbt-ammonite-classpath** is an sbt plug-in to export classpath of an sbt project to Ammonite Script, which can be then used in [Ammonite](https://ammonite.io/) or [Almond](http://almond.sh/).

## Usage

``` sbt
// project/plugins.sbt
addSbtPlugin("com.thoughtworks.deeplearning" % "sbt-ammonite-classpath" % "latest.release")
```

``` scala
// src/main/scala/mypackage/MyObject.scala
package mypackage

object MyObject {
  def hello() = println("Hello, World!")
}
```

``` bash
$ sbt Compile/fullClasspath/exportToAmmoniteScript && amm --predef target/scala-2.12/fullClasspath-Compile.sc
...
...
...
[success] Total time: 1 s, completed Apr 17, 2018 10:11:08 AM
Loading...
Compiling /private/tmp/example/target/scala-2.12/fullClasspath-Compile.sc
Welcome to the Ammonite Repl 1.1.0
(Scala 2.12.4 Java 1.8.0_162)
If you like Ammonite, please support our development at www.patreon.com/lihaoyi
@ mypackage.MyObject.hello() 
Hello, World!
```

Alternatively the classpath can be dynamically loaded by an `import $file` statement, too:

``` bash
$ amm
```

```
Loading...
Welcome to the Ammonite Repl 1.1.0
(Scala 2.12.4 Java 1.8.0_162)
If you like Ammonite, please support our development at www.patreon.com/lihaoyi
@ import $file.target.`scala-2.12`.`fullClasspath-Compile` 
Compiling /private/tmp/example/target/scala-2.12/fullClasspath-Compile.sc
import $file.$                                          

@ mypackage.MyObject.hello() 
Hello, World!
```

This plugin also supports directly running Ammonite Repl from sbt. Similar to using above scopes you may launch the Ammonite Repl with full classpath and compile scope as follows:

``` bash
sbt Compile/fullClasspath/launchAmmoniteRepl
```

## Related work

[sbt-ammonite](https://github.com/alexarchambault/sbt-ammonite) is an sbt 0.13 plug-in to launch Ammonite. It automatically passes the classpath instead of creating a `sc` file. However, it does not support Almond.

## Requirements

* Sbt 1.x
