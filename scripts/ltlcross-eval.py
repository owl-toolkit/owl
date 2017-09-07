#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import csv
import os
import sys
from builtins import tuple

if __name__ == "__main__":
    files = iter(sys.argv)
    next(files)
    files = tuple(files)
    for file in files:
        if not os.path.isfile(file):
            print("No file at " + file)
            exit(1)

    # Read all formulas, mapping tool -> formula -> stats
    numeric_keys = ['time', 'states', 'edges', 'transitions', 'acc', 'scc', 'nonacc_scc',
                    'strong_scc', 'weak_scc', 'terminal_scc']
    data = dict()
    tools = set()
    formulas = set()
    for file in files:
        with open(file, 'r') as csv_file:
            reader = csv.DictReader(csv_file, dialect='unix')
            # Each row are the values of some tool given a formula
            for row in reader:
                formula = row['formula']
                if not formula:
                    print("Row without formula in " + file)
                    exit(1)
                formulas.add(formula)

                tool = row['tool']
                # Dynamically discover tools
                if tool not in tools:
                    tools.add(tool)
                    data[tool] = dict()
                tool_data = data.get(tool)

                # Ignore rows where any key is missing (e.g. computation failed)
                if not all(key in row and row[key] != "" for key in numeric_keys):
                    continue

                # Create dictionary for this tool's stats on this formula
                stats = dict()
                for key in numeric_keys:
                    stats[key] = float(row[key])
                stats['status'] = row['exit_status']

                # Add the tool stats to the big dictionary
                tool_data[formula] = stats

    # Remove all formulas for which any tool failed
    failed_formulas = set()
    for tool, tool_data in data.items():
        missing_formulas = formulas.difference(tool_data.keys())
        for formula, stats in tool_data.items():
            if stats['status'] != "ok":
                failed_formulas.add(formula)

    if len(failed_formulas) == len(formulas):
        print("No formulas for which all tools succeeded")
        exit(0)

    # Compute statistics
    statistics = dict((tool, dict()) for tool in tools)

    for tool, tool_data in data.items():
        tool_values = dict((key, []) for key in numeric_keys)
        for formula, formula_stats in tool_data.items():
            for key in numeric_keys:
                tool_values.get(key).append(formula_stats[key])

        tool_stats = statistics[tool]
        for key in numeric_keys:
            sorted_values = sorted(tool_values[key])
            value_count = len(tool_values[key])
            mid = value_count // 2

            value_sum = sum(sorted_values)
            average = value_sum / value_count
            variance = (sum((value - average) ** 2 for value in sorted_values) / value_count)
            deviation = variance ** 0.5

            if value_count == 1:
                median = sorted_values[0]
            elif value_count % 2:  # uneven
                median = sorted_values[mid + 1]
            else:
                median = (sorted_values[mid] + sorted_values[mid + 1]) / 2

            tool_stats[key] = (average, median, deviation)

    # Formatting and printing

    def print_utf8(string):
        sys.stdout.buffer.write((string + "\n").encode("utf-8"))


    # Pre-format the cells in key -> tool -> data fashion
    stats_formatted = dict((key, dict()) for key in numeric_keys)
    for key in numeric_keys:
        for tool in tools:
            average, median, deviation = statistics[tool][key]
            average_deviation_string = "{0:.2f}±{1:2.2f}".format(average, deviation)
            median_string = "{0:.0f}".format(median)
            string = average_deviation_string + " (" + median_string + ")"
            stats_formatted[(key, tool)] = string

    maximum_tool_name_length = max(map(len, tools))
    maximum_cell_width = max(map(len, stats_formatted.values()))
    width = max(maximum_tool_name_length, maximum_cell_width)
    maximum_key_name_length = max(map(len, numeric_keys))
    tools = sorted(list(tools))

    header_string = "|".join([" {0:^{1}} ".format(tool, width)
                              for tool in tools])
    print_utf8("{0:>{1}}:{2}".format("tool", maximum_key_name_length, header_string))

    for key in numeric_keys:
        key_string = "|".join(" {0:>{1}s} ".format(stats_formatted[(key, tool)], width)
                              for tool in tools)
        print_utf8("{0:>{1}}:{2}".format(key, maximum_key_name_length, key_string))
