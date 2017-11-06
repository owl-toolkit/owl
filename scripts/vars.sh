#!/usr/bin/env bash
# Source this for common variables
SCRIPT_FOLDER="$(cd "$(dirname "$0")" && pwd -P)"
PROJECT_FOLDER="$(dirname "$SCRIPT_FOLDER")"
VERSION=$(grep "project.version" ${PROJECT_FOLDER}/build.gradle | \
    sed "s#project\.version \?= \?'\(.*\)'#\1#")