#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys

from . import defaults


class Tool(object):
    def __init__(self, name, execution):
        self.name = name
        self.execution = execution

    def get_name(self):
        return self.name

    def get_execution(self):
        return self.execution


def get_tool(tools_json, tool_description):
    # Tool description <name>(#(-?(flag|opt),)+)?

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
    optimisations = set()
    for modifier in modifiers:
        if modifier in tool_json.get("flags", {}):
            if modifier in exclusive_flags:
                for excluded_flag in exclusive_flags.get(modifier):
                    flags.pop(excluded_flag, None)
            flags[modifier] = tool_json["flags"][modifier]
        elif modifier in tool_json.get("optimisations", {}):
            optimisations.add(tool_json["optimisations"][modifier])
        elif modifier == "parallel":
            flags["parallel"] = "--parallel"
        elif modifier == "no-opt":
            optimisations.add("none")
        else:
            print("Unknown modifier {}. Known flags for tool {} are: {} and optimisations: {}"
                  .format(modifier, tool_name, ",".join(tool_json.get("flags", {}).keys()),
                          ",".join(tool_json.get("optimisations", {}).keys())), file=sys.stderr)
            sys.exit(1)

    tool_execution = [tool_json["executable"]]
    for flag in set(flags.values()):
        tool_execution.append(flag)
    if optimisations:
        tool_execution.append("--optimisations=" + ",".join(optimisations))

    return Tool(tool_name, tool_execution)