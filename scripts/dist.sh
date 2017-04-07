#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

PROJECT_FOLDER=$(dirname $(dirname $(realpath $0)))

VERSION=$(grep "project.version" ${PROJECT_FOLDER}/build.gradle \
            | sed "s#project\.version \?= \?'\(.*\)'#\1#i")

${PROJECT_FOLDER}/gradlew --no-daemon distTar
tar xvf ${PROJECT_FOLDER}/build/distributions/owl-${VERSION}.tar -C build --strip-components=1