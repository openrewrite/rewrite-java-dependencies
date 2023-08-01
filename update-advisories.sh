#!/bin/bash
set -ex

./gradlew build deps
java -classpath "build/deps/*:build/libs/*" \
  org.openrewrite.java.dependencies.github.ParseAdvisories \
  build/advisories \
  src/main/resources/advisories.csv
