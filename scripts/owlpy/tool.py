#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os

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
    def __init__(self, name, tool, flags, input_type, output_type, pre=None, post=None):
        super().__init__(name, flags)
        if post is None:
            post = []
        if pre is None:
            pre = []
        self.pre = pre
        self.tool = tool
        self.post = post
        self.input_type = input_type
        self.output_type = output_type

    def get_pipeline(self):
        pipeline = []
        # Input
        if self.input_type == "ltl":
            pipeline.append("ltl")
        else:
            raise KeyError("Unknown input type {} for {}".format(self.input_type, self.name))
        pipeline.append("---")

        # Pre-processing
        for pre_command in self.pre:
            if isinstance(pre_command, str):
                pipeline.append(pre_command)
            else:
                pipeline.extend(pre_command)
            pipeline.append("---")

        # Tool execution
        pipeline.append(self.tool)
        pipeline.extend(set(self.flags.values()))
        pipeline.append("---")

        # Post-processing
        for post_command in self.post:
            if isinstance(post_command, str):
                pipeline.append(post_command)
            else:
                pipeline.extend(post_command)
            pipeline.append("---")

        # Output
        if self.output_type == "hoa":
            pipeline.append("hoa")
        else:
            raise KeyError("Unknown input type {} for {}".format(self.input_type, self.name))
        return pipeline

    def get_server_execution(self, port=None):
        if os.name == 'nt':
            tool_execution = ["build\\bin\\owl-server.bat"]
        else:
            tool_execution = ["build/bin/owl-server"]
        if port is not None:
            tool_execution.extend(["--port", str(port)])

        tool_execution.extend(self.get_pipeline())
        return tool_execution

    def get_file_execution(self, file):
        if os.name == 'nt':
            tool_execution = ["build\\bin\\owl.bat"]
        else:
            tool_execution = ["build/bin/owl"]
        tool_execution.extend(["-I", file])
        tool_execution.extend(self.get_pipeline())
        return tool_execution

    def get_input_execution(self, static_input):
        if os.name == 'nt':
            tool_execution = ["build\\bin\\owl.bat"]
        else:
            tool_execution = ["build/bin/owl"]
        # TODO Fix --worker flag
        tool_execution.extend(["-i", static_input]) # , "--worker", "0"])
        tool_execution.extend(self.get_pipeline())
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
        if "input" not in tool_json:
            raise KeyError("No input type specified for {}".format(tool_name))
        input_type = tool_json["input"]
        pre = tool_json.get("pre", [])

        if "name" not in tool_json:
            raise KeyError("No name specified for {}".format(tool_name))
        tool = tool_json["name"]
        post = tool_json.get("post", [])

        if "output" not in tool_json:
            raise KeyError("No output type specified for {}".format(tool_name))
        output_type = tool_json["output"]

        tool = OwlTool(tool_name, tool, flags, input_type, output_type, pre, post)
    elif tool_type == "spot":
        tool_execution = [tool_json["executable"]]
        for flag in set(flags.values()):
            tool_execution.append(flag)
        tool = SpotTool(tool_name, tool_execution, flags)
    else:
        raise KeyError("Unknown tool type {}".format(tool_type))

    return tool
