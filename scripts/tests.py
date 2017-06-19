#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json

import os
import subprocess
import sys

script_dir = os.path.dirname(os.path.realpath(__file__))

sys.path.append(script_dir)
import tools as tool_query

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: tests.py <test name>")
        sys.exit(1)

    with open(os.path.join(script_dir, "tests.json"), 'r') as f:
        config = json.load(f)

    test_name = sys.argv[1]

    if test_name not in config["tests"]:
        print("Unknown test case {}".format(test_name))
        sys.exit(1)

    defaults_json = config["defaults"]
    dataset_json = config["dataset"]
    test_json = config["tests"][test_name]

    if type(test_json) is str:
        tools = [test_json]
        reference = defaults_json["reference"]
        data = defaults_json["data"]
    else:
        if "reference" in test_json:
            reference = test_json["reference"]
        else:
            reference = defaults_json["reference"]

        if type(test_json["tools"]) is str:
            tools = [test_json["tools"]]
        else:
            tools = test_json["tools"]

        if "data" in test_json:
            data = test_json["data"]
        else:
            data = defaults_json["data"]

    if type(data) is str:
        if data in dataset_json:
            data = dataset_json[data]
        else:
            print("Unknown dataset {}".format(data))
            sys.exit(1)

    test_arguments = [os.path.join(script_dir, "ltlcross-run.sh"),
                      reference["name"], " ".join(reference["exec"])]

    for tool in tools:
        test_arguments.append("-t")
        if type(tool) is str:
            loaded_tool = tool_query.get_tool(tool)
            if loaded_tool is None:
                print("Unknown tool {}".format(tool))
                sys.exit(1)
            if "name" not in loaded_tool:
                test_arguments.append(tool)
            else:
                test_arguments.append(loaded_tool["name"])

            test_arguments.append(" ".join(loaded_tool["exec"]) + " %f")
        else:
            test_arguments.append(tool["name"])
            test_arguments.append(" ".join(tool["exec"]))

    for data_set in data:
        if data_set.get("determinize", False):
            test_arguments.append("-d")

        data_set_type = data_set.get("type", "file")
        if data_set_type == "file":
            path = data_set["path"]
            test_arguments.append(path)
        elif data_set_type == "random":
            test_arguments.append("random")
        else:
            print("Unknown data set type {}".format(data_set_type))
            sys.exit(1)

    sub_env = os.environ.copy()
    sub_env["JAVA_OPTS"] = "-enableassertions -Xss64M"
    process = subprocess.run(test_arguments, env=sub_env)
    sys.exit(process.returncode)
