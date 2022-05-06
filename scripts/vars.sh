#!/usr/bin/env bash

#
# Copyright (C) 2016 - 2021  (See AUTHORS)
#
# This file is part of Owl.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

# Source this for common variables
script_folder="$(cd "$(dirname "$0")" && pwd -P)"
export script_folder
project_folder="$(dirname "$script_folder")"
export project_folder
version=$(grep "version = " "${project_folder}/build.gradle.kts" | sed "s!version \?= \?'\(.*\)'!\1!")
export version

# Prefer python3 if installed (ubuntu / debian)
if command -v python3 >/dev/null 2>&1; then
  python="python3"
else
  python="python"
fi
export python