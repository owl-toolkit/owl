#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import os.path as path
import subprocess
import sys

import owlpy.defaults as owl_defaults
import owlpy.formula as owl_formula
import owlpy.tool as owl_tool


def _test(args):
    if len(args) > 2:
        print("Usage: util.py test <database>? <test name>")
        sys.exit(1)

    if len(args) == 1:
        database = owl_defaults.get_test_path()
        test_name = args[0]
    else:
        database = args[0]
        test_name = args[1]

    test_config = owl_defaults.load_json(database)

    if test_name not in test_config["tests"]:
        raise KeyError("Unknown test case {0!s}".format(test_name))

    defaults_json = test_config["defaults"]
    dataset_json = test_config["dataset"]
    test_json = test_config["tests"][test_name]

    if type(test_json) is str:
        test_json = {"tools": [test_json]}

    for key, value in defaults_json.items():
        if key not in test_json:
            test_json[key] = value

    reference = test_json["reference"]
    if type(test_json["tools"]) is str:
        tools = [test_json["tools"]]
    else:
        tools = test_json["tools"]
    data = test_json["data"]
    if type(data) is str:
        if data in dataset_json:
            data = dataset_json[data]
        else:
            data = [data]

    test_arguments = [owl_defaults.get_script_path("ltlcross-run.sh"),
                      reference["name"], " ".join(reference["exec"])]

    loaded_tools = []
    for tool_description in tools:
        if type(tool_description) is str:
            tool_database = owl_defaults.load_json(owl_defaults.get_tool_path())
            loaded_tools.append(owl_tool.get_tool(tool_database, tool_description))
        elif type(tool_description) is dict:
            loaded_tools.append(owl_tool.Tool(tool_description["name"], tool_description["exec"]))
        else:
            raise TypeError("Unknown tool type {0!s}".format(type(tool_description)))

    for loaded_tool in loaded_tools:
        test_arguments.append("-t")
        test_arguments.append(loaded_tool.get_name())
        execution = loaded_tool.get_execution()
        if "%f" not in execution:
            execution.append("%f")
        test_arguments.append(" ".join(execution))

    formulas_json = owl_defaults.load_json(owl_defaults.get_formula_path())
    formula_sets = owl_formula.read_formula_sets(formulas_json)

    for data_set in data:
        if type(data_set) is dict:
            if "name" not in data_set:
                raise KeyError("No dataset name provided")
            data_set_name = data_set["name"]
            data_set_det = data_set.get("determinize", False)
        elif type(data_set) is str:
            data_set_name = data_set
            data_set_det = False
        else:
            raise TypeError("Unknown specification format")
        if data_set_name not in formula_sets:
            raise KeyError("Unknown formula set {0!s}".format(data_set_name))

        if data_set_det:
            test_arguments.append("-d")
        test_arguments.append(data_set_name)

    sub_env = os.environ.copy()
    sub_env["JAVA_OPTS"] = "-enableassertions -Xss64M"
    process = subprocess.run(test_arguments, env=sub_env)
    sys.exit(process.returncode)


def _formula(args):
    if len(args) > 1 and path.exists(args[0]):
        database = args[0]
        sets = args[1:]
    else:
        database = owl_defaults.get_formula_path()
        sets = args

    formulas = owl_formula.read_formula_sets(owl_defaults.load_json(database))

    for set_name in sets:
        if set_name not in formulas:
            raise KeyError("Unknown set {0!s}".format(set_name))

    for set_name in sets:
        for formula in formulas[set_name]:
            print(formula)

    sys.exit(0)


def _tool(args):
    if len(args) > 2:
        print("Usage: util.py tool <database>? <tool name>")
        sys.exit(1)

    if len(args) == 2:
        database = args[0]
        tool_name = args[1]
    else:
        database = owl_defaults.get_tool_path()
        tool_name = args[0]

    for line in owl_tool.get_tool(owl_defaults.load_json(database), tool_name).get_execution():
        print(line)

    sys.exit(0)


def _benchmark(args):
    if len(args) > 2:
        print("Usage: util.py bench <database>? <benchmark name>")

    if len(args) == 2:
        database = args[0]
        benchmark_name = args[1]
    else:
        database = owl_defaults.get_benchmark_path()
        benchmark_name = args[0]

    benchmarks = owl_defaults.load_json(database)
    defaults_json = benchmarks["defaults"]
    dataset_json = benchmarks["dataset"]

    if benchmark_name not in benchmarks["benchmark"]:
        raise KeyError("Unknown benchmark {0!s}".format(benchmark_name))

    benchmark_json = benchmarks["benchmark"][benchmark_name]
    if type(benchmark_json) is str:
        benchmark_json = {"tool": benchmark_json}

    for key, value in defaults_json.items():
        if key not in benchmark_json:
            benchmark_json[key] = value

    tool_description = benchmark_json["tool"]
    data = benchmark_json.get("data")
    repeat = benchmark_json.get("repeat")
    update = benchmark_json.get("update")
    perf = benchmark_json.get("perf", None)

    if type(data) is str:
        if data in dataset_json:
            data = dataset_json[data]
        else:
            data = [data]

    if type(tool_description) is str:
        tool_database = owl_defaults.load_json(owl_defaults.get_tool_path())
        tool = owl_tool.get_tool(tool_database, tool_description)
    elif type(tool_description) is dict:
        tool = owl_tool.Tool(tool_description["name"], tool_description["exec"])
    else:
        raise TypeError("Unknown tool type {0!s}".format(type(tool_description)))

    benchmark_script = [owl_defaults.get_script_path("benchmark.sh"), "--stdin",
                        "--repeat", str(repeat)]
    if update:
        benchmark_script += ["--update"]
    if perf:
        benchmark_script += ["--perf"]
    elif perf is not None:
        benchmark_script += ["--time"]

    benchmark_script += ["--"] + tool.get_execution()

    formulas = owl_formula.read_formula_sets(
        owl_defaults.load_json(owl_defaults.get_formula_path()))
    for formula_set in data:
        if formula_set not in formulas:
            raise KeyError("Unknown formula set {0!s}".format(formula_set))

    benchmark_input = "\n".join(["\n".join(formulas[formula_set]) for formula_set in data])
    benchmark = subprocess.run(benchmark_script, input=benchmark_input.encode("utf-8"))
    sys.exit(benchmark.returncode)


if __name__ == "__main__":
    if len(sys.argv) == 1:
        print("Usage: util.py <type> <args>")
        sys.exit(1)

    task_type = sys.argv[1]

    try:
        if task_type == "test":
            _test(sys.argv[2:])
        elif task_type == "formula":
            _formula(sys.argv[2:])
        elif task_type == "tool":
            _tool(sys.argv[2:])
        elif task_type == "bench":
            _benchmark(sys.argv[2:])
        else:
            print("<type> must be one of test, formula, tool or bench")
            sys.exit(1)
    except KeyboardInterrupt:
        print("Interrupted")
        sys.exit(1)
    except Exception as e:
        print("Error: {0!s}".format(e), file=sys.stderr)
        raise e
        sys.exit(1)
