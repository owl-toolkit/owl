# Owl 

[![build status](https://gitlab.lrz.de/i7/owl/badges/master/build.svg)](https://gitlab.lrz.de/i7/owl/commits/master)

[Website](https://owl.model.in.tum.de/)

## About

Owl (**O**mega **W**ord and automata **L**ibrary) is a library tailored for &mdash; but not limited to &mdash; semantic-based translations from LTL to deterministic automata.
It ships basic building blocks for constructing omega-automata and includes several command-line tools implementing these translations.

## Interaction

For details on how to use and integrate with `Owl`, see the [usage instructions](doc/USAGE.md).
This is sufficient for users working with `Owl`.

To build `Owl` from source, the [building instructions](doc/BUILDING.md) may be of use.
Note that the default distribution does not contain the gradle script necessary to build `Owl`, instead, clone the [repository](https://gitlab.lrz.de/i7/owl).

If you want to contribute to `Owl`, read the [contribution guidelines](CONTRIBUTING.md) which are mandatory if you want your changes to be merged into the main branch.
Read the javadoc of the respective packages of the infrastructure you plan to use, e.g., `owl.automaton`.
It contains links to the relevant classes and typical use cases.

## Publications

Please see the [citing section of the official website for an updated list](https://owl.model.in.tum.de/#citing).