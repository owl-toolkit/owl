#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import csv
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

    # Read all formulae, mapping formula -> tool -> stats
    numeric_keys = ['time', 'states', 'edges', 'transitions', 'acc', 'scc', 'nonacc_scc',
                    'strong_scc', 'weak_scc', 'terminal_scc']
    tools = set()
    formula_dict = dict()
    for file in files:
        with open(file, 'r') as csv_file:
            reader = csv.DictReader(csv_file, dialect='unix')
            for row in reader:
                tool = row['tool']
                if tool not in tools:
                    tools.add(tool)
                formula = row['formula']
                if not formula:
                    print("Row without formula in " + file)
                    exit(1)
                if not all(key in row and row[key] != "" for key in numeric_keys):
                    continue
                tool_dict = dict()
                for key in numeric_keys:
                    tool_dict[key] = float(row[key])
                tool_dict['status'] = row['exit_status']
                tools_dict = formula_dict.setdefault(formula, dict())
                tools_dict[tool] = tool_dict

    # Remove all failed formulae
    failed_formulae = list()
    for formula, formula_tools in formula_dict.items():
        if not all(tool in formula_tools and formula_tools[tool]['status'] == "ok"
                   for tool in tools):
            failed_formulae.append(formula)
    for formula in failed_formulae:
        del formula_dict[formula]

    if not formula_dict:
        print("No formulae for which all tools succeeded")
        exit(0)

    # Compute averages
    averages = dict()
    for key in numeric_keys:
        tool_sum = dict((tool, 0) for tool in tools)
        for formula, formula_tools in formula_dict.items():
            for tool, data in formula_tools.items():
                tool_sum[tool] += data[key]
        averages[key] = dict([(tool, tool_sum[tool] / len(formula_dict))
                              for tool in tools])

    # Formatting and printing
    maximum_tool_name_length = max(map(len, tools))
    maximum_key_name_length = max(map(len, numeric_keys))
    tools = sorted(list(tools))

    header_string = " |".join(["{0:>{1}}".format(tool, maximum_tool_name_length + 1)
                               for tool in tools])
    print("{0:>{1}}:{2}".format("tool", maximum_key_name_length, header_string))

    for key in numeric_keys:
        key_data = averages[key]
        key_string = " |".join("{0:{1}.2f}".format(key_data[tool], maximum_tool_name_length + 1)
                               for tool in tools)
        print("{0:>{1}}:{2}".format(key, maximum_key_name_length, key_string))
