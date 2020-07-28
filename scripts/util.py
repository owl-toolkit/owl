#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import os.path as path
import subprocess
import sys
from os.path import relpath

import owlpy.defaults as owl_defaults
import owlpy.formula as owl_formula

def _test(args, check):
    if len(args) > 2:
        if check:
            print("Usage: util.py test <test names> <dataset>?")
        else:
            print("Usage: util.py stats <test names> <dataset>?")
        sys.exit(1)

    database = owl_defaults.get_test_path()
    test_names = args[0].split(";")
    test_config = owl_defaults.load_json(database)

    for test_name in test_names:
        if test_name not in test_config["tests"]:
            raise KeyError("Unknown test case {0!s}".format(test_name))

    dataset_json = test_config["dataset"]

    print("Running tests for datasets " + ", ".join(test_names))
    print()
    sys.stdout.flush()

    for test_name in test_names:
        test_json = test_config["tests"][test_name]

        if type(test_json["tools"]) is str:
            tools = [test_json["tools"]]
        else:
            tools = test_json["tools"]

        test_data_sets = test_json["data"]

        if test_data_sets in dataset_json:
           test_data_sets = dataset_json[test_data_sets]
        else:
           test_data_sets = [test_data_sets]

        if check:
            test_arguments = [str(relpath(owl_defaults.get_script_path("ltlcross-run.sh"))), "ltl2tgba (Spot)", "ltl2tgba -H -f %f"]
        else:
            test_arguments = [str(relpath(owl_defaults.get_script_path("ltlcross-bench.sh")))]

        loaded_tools = []

        if type(tools) is list:
            for tool_description in tools:
                loaded_tools.append(tool_description)
        elif type(tools) is str:
            loaded_tools.append(tools)
        else:
            raise TypeError("Unknown tools type {0!s}".format(type(tools)))

        for loaded_tool in loaded_tools:
            test_arguments.append("-t")
            test_arguments.append(loaded_tool.replace("<input>", "").replace("  ", " "))
            if loaded_tool.startswith("ltl2tgba"):
                test_arguments.append(loaded_tool.replace("<input>", "-f %f"))
            else:
                test_arguments.append("./owl " + loaded_tool.replace("<input>", "-f %f"))

        formulas_json = owl_defaults.load_json(owl_defaults.get_formula_path())
        formula_sets = owl_formula.read_formula_sets(formulas_json)

        for data_set in test_data_sets:
            if type(data_set) is dict:
                if "name" not in data_set:
                    raise KeyError("No dataset name provided")
                formula_set_name = data_set["name"]
                if data_set.get("determinize", False):
                    test_arguments.append("-d")
            elif type(data_set) is str:
                formula_set_name = data_set
            else:
                raise TypeError("Unknown specification format")

            if formula_set_name not in formula_sets:
                raise KeyError("Unknown formula set {0!s}".format(formula_set_name))
            test_arguments.append(formula_set_name)

        sub_env = os.environ.copy()
        process = subprocess.run(test_arguments, env=sub_env)

        if process.returncode:
            sys.exit(process.returncode)

    sys.exit(0)

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
    if len(args) > 1:
        print("Usage: util.py bench <benchmark name>")

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
    else:
        raise TypeError("Unknown tool description type {0!s}".format(type(tool_description)))

    benchmark_script = [owl_defaults.get_script_path("benchmark.sh"), "--stdin",
                        "--repeat", str(repeat)]

    if update:
        benchmark_script += ["--update"]
    if perf:
        benchmark_script += ["--perf"]
    elif perf is not None:
        benchmark_script += ["--time"]

    benchmark_script += ["--"] + tool.get_file_execution("%F")

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

    args = sys.argv
    task_type = args[1]

    try:
        if task_type == "test":
            _test(args[2:], True)
        elif task_type == "stats":
            _test(args[2:], False)
        elif task_type == "formula":
            _formula(args[2:])
        elif task_type == "tool":
            _tool(args[2:])
        elif task_type == "bench":
            _benchmark(args[2:])
        else:
            print("<type> must be one of test, stats, formula, tool or bench")
            sys.exit(1)
    except KeyboardInterrupt:
        print("Interrupted")
        sys.exit(1)
    except Exception as e:
        print("Error: {0!s}".format(e), file=sys.stderr)
        raise e
        sys.exit(1)
