#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json

import os.path as path
import sys


def get_tool(tool_description):
    # Tool description <name>(#(-?(flag|opt),)+)?
    script_dir = path.dirname(path.realpath(__file__))

    with open(path.join(script_dir, "tool-configurations.json"), 'r') as f:
        config = json.load(f)

    if tool_description in config.get("aliases", {}):
        tool_spec = config["aliases"][tool_description]
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

    if tool_name not in config["tools"]:
        print("Unknown tool {}".format(tool_name), file=sys.stderr)
        return None

    tool_json = config["tools"][tool_name]
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
            flags["parallel"] = "parallel"
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

    return {"name": tool_name, "exec": tool_execution}


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(1)

    tool = get_tool(sys.argv[1])
    if tool is None:
        sys.exit(1)

    for tool_exec in tool["exec"]:
        print(tool_exec)

    sys.exit(0)
