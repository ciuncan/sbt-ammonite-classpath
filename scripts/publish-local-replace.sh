#!/bin/bash

plugin_file="$HOME/.sbt/1.0/plugins/build.sbt"

cat "$plugin_file"
sed '/sbt-ammonite-classpath/d' -i "$plugin_file"

version="$(sbt "show publishLocal" | grep delivering | cut -d';' -f2 | cut -d' ' -f1 | head -n 1)"

echo 'addSbtPlugin("com.thoughtworks.deeplearning" % "sbt-ammonite-classpath" % "'"$version"'")' >> "$plugin_file"
cat "$plugin_file"