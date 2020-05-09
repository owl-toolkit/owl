#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import csv
import os
import sys
from collections import defaultdict
from statistics import mean, median


def die(message, code=1):
    print(message, file=sys.stderr)
    exit(code)


aut_input_key = "input.source"
aut_input_numeric_keys = {'input.states': 'states',
                          'input.edges': 'edges',
                          'input.transitions': 'trans',
                          'input.acc_sets': 'acc',
                          'input.scc': 'scc'
                          }
aut_numeric_keys = {'time': 'time',
                    'output.states': 'states',
                    'output.edges': 'edges',
                    'output.transitions': 'trans',
                    'output.acc_sets': 'acc',
                    'output.scc': 'scc'
                    }
aut_output_names = ["time", "states", "edges", "trans", "acc", "scc"]

ltl_input_key = "formula"
ltl_numeric_keys = {'time': 'time',
                    'states': 'states',
                    'edges': 'edges',
                    'transitions': 'trans',
                    'acc': 'acc',
                    'scc': 'scc'
                    }
ltl_output_names = ["time", "states", "edges", "trans", "acc", "scc", "scc(na)", "scc(st)",
                    "scc(wk)", "scc(tm)"]

if __name__ == "__main__":
    if len(sys.argv) <= 2 or sys.argv[1] not in {"ltl", "aut"}:
        die(f"Usage: {sys.argv[0]} <mode ltl|aut> <files ...>")

    mode = sys.argv[1]
    files = tuple(sys.argv[2:])
    for file in files:
        if not os.path.isfile(file):
            die(f"No file at {file}")

    if mode == "ltl":
        input_key_header, numeric_keys, output_names = \
            ltl_input_key, ltl_numeric_keys, ltl_output_names
    elif mode == "aut":
        input_key_header, numeric_keys, output_names = \
            aut_input_key, aut_numeric_keys, aut_output_names
    else:
        raise AssertionError()

    data = defaultdict(dict)
    input_keys = set()

    for file in files:
        with open(file, mode="rt", encoding="utf-8") as csv_file:
            reader = csv.DictReader(csv_file, dialect='unix')

            # Each row are the values of some tool given a formula
            for row in reader:
                key = row.get(input_key_header, None)
                if not key:
                    die(
                        f"Row {row} without key {input_key_header} in {file}: {'|'.join(row.keys())}")
                input_keys.add(key)

                tool = row['tool']
                tool_data = data[tool]

                # Ignore rows where any key is missing (e.g. computation failed)
                if not all(stat_key in row and row[stat_key] != ""
                           for stat_key in numeric_keys.keys()):
                    continue

                # Create dictionary for this tool's stats on this formula
                stats = dict((stat_name, float(row[stat_key]))
                             for stat_key, stat_name in numeric_keys.items())
                stats["status"] = row["exit_status"]

                if mode == "aut":
                    if key not in data["input"]:
                        input_stats = dict((stat_name, float(row[stat_key]))
                                           for stat_key, stat_name in
                                           aut_input_numeric_keys.items())
                        data["input"][key] = input_stats
                        data["input"][key]["status"] = "ok"
                        data["input"][key]["time"] = 0.0

                tool_data[key] = stats

    failed_keys = set()
    for tool, tool_data in data.items():
        failures = 0
        for key in input_keys:
            if key not in tool_data or tool_data[key]["status"] != "ok":
                failures += 1
                failed_keys.add(key)
        if failures:
            print(f"{tool} failed {failures} time(s)")

    if len(failed_keys) == len(input_keys):
        die("No input for which all tools succeeded")

    values = {}
    for tool, tool_data in data.items():
        tool_values = dict((name, []) for name in numeric_keys.values())
        values[tool] = tool_values
        for input_key, formula_stats in tool_data.items():
            if input_key in failed_keys:
                continue
            for name in numeric_keys.values():
                tool_values[name].append(formula_stats[name])

    tools = sorted(list(data.keys()))
    table_header = [""]
    for name in output_names:
        table_header += [name, ""]

    if "input" in tools:
        tools.remove("input")
        tools = ["input"] + tools

    table_data = []
    for tool in tools:
        tool_values = values[tool]
        table_tool_data = [tool]
        for name in output_names:
            if name not in tool_values:
                continue
            val = tool_values[name]
            # data_string = f"{mean(val) :.2f}±{stdev(val):.2f} ({median(val):.0f})"
            val_median = median(val)
            val_mean = mean(val)
            top_avg = mean(sorted(val)[len(val) * 99 // 100:])
            table_tool_data.extend([f"{mean(val):.1f}", f"({top_avg:.1f})"])
        table_data.append(table_tool_data)

    try:
        import tabulate
        print(tabulate.tabulate(table_data, headers=table_header, tablefmt="simple"))
    except ModuleNotFoundError:
        print("No tabulate module found, printing raw")
        print(" | ".join(map(str, table_header)))
        for row in table_data:
            print(" | ".join(map(str, row)))