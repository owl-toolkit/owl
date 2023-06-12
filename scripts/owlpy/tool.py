#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
from os.path import dirname, join, exists, relpath


def get_bin_dir():
    return join(dirname(dirname(dirname(__file__))), "build", "bin")


def is_native_available():
    return exists(join(get_bin_dir(), "owl-native"))


def get_owl_executable():
    bin_dir = get_bin_dir()
    native_path = join(bin_dir, "owl-native")
    if os.path.exists(native_path):
        return relpath(native_path)
    return relpath(join(bin_dir, "owl"))


class Tool(object):
    def __init__(self, name, flags):
        self.name = name
        self.flags = flags

    def get_name(self):
        return self.name


class SpotTool(Tool):
    def __init__(self, name, execution, flags):
        super().__init__(name, flags)
        self.execution = execution

    def get_file_execution(self, file):
        tool_execution = list(self.execution)
        tool_execution.extend(["-F", file])
        return tool_execution

    def get_input_execution(self, static_input):
        tool_execution = list(self.execution)
        tool_execution.extend(["-f", static_input])
        return tool_execution


class OwlTool(Tool):
    def __init__(self, name, tool, flags):
        super().__init__(name, flags)
        self.tool = tool

    def get_file_execution(self, file):
        tool_execution = [get_owl_executable()]
        tool_execution.append(self.tool)
        tool_execution.append("--run-in-non-native-mode")
        tool_execution.extend(self.flags.values())
        tool_execution.extend(["-i", file])
        return tool_execution

    def get_input_execution(self, static_input):
        tool_execution = [get_owl_executable()]
        tool_execution.append(self.tool)
        tool_execution.append("--run-in-non-native-mode")
        tool_execution.extend(self.flags.values())
        tool_execution.extend(["-f", static_input])
        return tool_execution


def get_tool(tools_json, tool_description):
    # Tool description <name>(#(-?(flag),)+)?

    if tool_description in tools_json.get("aliases", {}):
        tool_spec = tools_json["aliases"][tool_description]
    else:
        tool_spec = tool_description

    if "#" in tool_spec:
        parts = tool_spec.split("#")
        if len(parts) != 2:
            print("Tool specification may only contain one #", file=sys.stderr)
            sys.exit(1)
        tool_name = parts[0]
        tool_modifier = parts[1].split(",")
    else:
        tool_name = tool_spec
        tool_modifier = []

    if tool_name not in tools_json["tools"]:
        raise KeyError("Unknown tool {}".format(tool_name))

    tool_json = tools_json["tools"][tool_name]
    modifiers = set()
    if "defaults" in tool_json:
        for modifier in tool_json["defaults"]:
            modifiers.add(modifier)

    if "type" not in tool_json:
        raise KeyError("No type specified for {}".format(tool_name))

    tool_type = tool_json["type"]

    for modifier in tool_modifier:
        if not modifier:
            continue
        if modifier.startswith("-"):
            modifiers.remove(modifier[1:])
        else:
            modifiers.add(modifier)

    exclusive_flags = {}
    for exclusion_list in tool_json.get("exclusive-flags", []):
        for exclusive_flag in exclusion_list:
            if exclusive_flag in exclusive_flags:
                exclusion_set = exclusive_flags.get(exclusive_flag)
            else:
                exclusion_set = set()
                exclusive_flags[exclusive_flag] = exclusion_set
            for other_flag in exclusion_list:
                if other_flag != exclusive_flag:
                    exclusion_set.add(other_flag)

    flags = dict()
    for modifier in modifiers:
        if modifier in tool_json.get("flags", {}):
            if modifier in exclusive_flags:
                for excluded_flag in exclusive_flags.get(modifier):
                    flags.pop(excluded_flag, None)
            flags[modifier] = tool_json["flags"][modifier]
        else:
            raise KeyError("Unknown modifier {}. Known flags for {} are: {}".format(
                modifier, tool_name, ",".join(tool_json.get("flags", {}).keys())))

    if tool_type == "owl":
        if "name" not in tool_json:
            raise KeyError("No name specified for {}".format(tool_name))
        tool = tool_json["name"]
        tool = OwlTool(tool_name, tool, flags)
    elif tool_type == "spot":
        tool_execution = [tool_json["executable"]]
        for flag in set(flags.values()):
            tool_execution.append(flag)
        tool = SpotTool(tool_name, tool_execution, flags)
    else:
        raise KeyError("Unknown tool type {}".format(tool_type))

    return tool
