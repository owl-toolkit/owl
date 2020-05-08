#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os.path as path
import os

_loaded_json = dict()


def _get_dir():
    return path.dirname(path.realpath(__file__))


def get_script_path(script):
    return path.normpath(path.join(_get_dir(), "..", script))


def get_data_path(file=None):
    directory = path.normpath(path.join(_get_dir(), "..", "..", "data"))
    if file is None:
        return directory
    return path.join(directory, file)


def get_formula_path():
    return get_data_path("formulas.json")


def get_tool_path():
    return get_data_path("tools.json")


def get_test_path():
    return get_data_path("tests.json")


def get_benchmark_path():
    return get_data_path("benchmarks.json")


def load_json(json_path):
    import json

    json_path = path.abspath(json_path)
    if json_path in _loaded_json:
        return _loaded_json[json_path]
    with open(json_path, 'r') as f:
        json_object = json.load(f)
    _loaded_json[json_path] = json_object
    return json_object
