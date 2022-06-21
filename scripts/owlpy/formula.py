#!/usr/bin/env python3
# -*- coding: utf-8 -*-

class FormulaSet(object):
    def get_formulas(self):
        raise NotImplementedError()

    def __iter__(self):
        return iter(self.get_formulas())

    def __contains__(self, item):
        return item in self.get_formulas()

    def __len__(self):
        return len(self.get_formulas())


class FileFormulaSet(FormulaSet):
    def __init__(self, path):
        self.path = path
        self._formulas = None

    def get_path(self):
        return self.path

    def get_formulas(self):
        if self._formulas is None:
            with open(self.path, 'r') as f:
                formulas = f.read().splitlines()
            formulas = list(filter(lambda s: len(s) > 0 and not s.startswith('#'),
                                   map(str.strip, formulas)))
            self._formulas = formulas
        return self._formulas


class RandomFormulaSet(FormulaSet):
    def __init__(self, count, seed, size):
        self._formulas = None
        self._count = count
        self._seed = seed
        self._size = size

    def get_formulas(self):
        if self._formulas is None:
            args = ["randltl", "--seed=" + str(self._seed), "--formulas=" + str(self._count), "a", "b", "c", "d", "e", "f",
                    "--tree-size=5.." + str(self._size)]
            import subprocess
            formulas = subprocess.check_output(args).decode("utf-8")
            self._formulas = str.splitlines(formulas)
        return self._formulas

def read_formula_sets(data_json):
    formula_sets = dict()
    if type(data_json) is not dict:
        raise TypeError("Expected formula map")
    for name, data in data_json.items():
        if "type" not in data:
            raise KeyError("Formula {0!s} has no type".format(name))
        set_type = data["type"]
        if set_type == "file":
            if "path" not in data:
                raise KeyError("No path for file set {0!s}".format(name))
            formula_sets[name] = FileFormulaSet(path=data["path"])
        elif set_type == "random":
            import random
            formula_sets[name] = RandomFormulaSet(100, random.randint(0, 32000), 30)
        else:
            raise KeyError("Unknown type {0!s}".format(set_type))

    return formula_sets
