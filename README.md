# OWL 

[![build status](https://gitlab.lrz.de/i7/owl/badges/master/build.svg)](https://gitlab.lrz.de/i7/owl/commits/master)

## About

Owl (**O**mega **W**ord and automata **L**ibrary) is a library tailored for --- but not limited to --- semantic-based translations from LTL to deterministic automata. It ships basic building blocks for constructiong omega-automata and includes several command-line tools implementing these translations.

## Building

### Tools

1. Build the project using gradle. All dependencies are downloaded automatically.

    `$ ./gradlew build`  

2. Unzip the tools located in `build/distributions`

### Library

1. Build the project using gradle. All dependencies are downloaded automatically.

    $ ./gradlew build  

2. Install the `jar` into the local maven cache

    $ ./gradlew install

## Tool Guide

### General Commandline Flags

The following flags are understood by all tools contained in Owl:

* `--annotations` - Annotate all states with their semantic meaning.
* `--state-acceptance` - Output the automaton with state acceptance.

### ltl2ldba

`ltl2ldba` produces in its default configuration limit-determinisitc generalized Büchi automata and enables all optimisations. The formula can either be passed as an argument or piped to `stdin`. The automaton is always written to `stdout`. To construct the automaton for an LTL formula simply type: 

    $ ./Tools/owl-1.0.0/bin/ltl2ldba "! (a U (b W c))"
    $ echo "a -> X b" | ./Tools/owl-1.0.0/bin/ltl2ldba

To obtain a Büchi automaton pass use the `--Buchi` flag:

    $ ./Tools/owl-1.0.0/bin/ltl2ldba --Buchi "(F G (a U (! X b))) & G F c"

To obtain to a non-deterministic initial component pass `-n`:

    $ ./Tools/owl-1.0.0/bin/ltl2ldba -n "F (a & X X X b)"

### ltl2dpa

`ltl2dpa` produces a deterministic parity automaton. The formula can either be passed as an argument or piped to `stdin`. The automaton is always written to `stdout`.

    $ ./Tools/owl-1.0.0/bin/ltl2dpa "! (a U (b W c))"
    $ echo "a -> X b" | ./Tools/owl-1.0.0/bin/ltl2dpa

Furthermore, the parallel mode can be enable by passing: `--parallel`

    $ ./Tools/owl-1.0.0/bin/ltl2dpa --parallel "(F G (a U (! X b))) & G F c"

### ltl2da

`ltl2da` selects automatically the smallest deterministic automaton it can produce. It has the same usage as `ltl2dpa`.

    $ ./Tools/owl-1.0.0/bin/ltl2da "! (a U (b W c)))"

### nba2ldba

`nba2ldba` reads a non-deterministic Büchi automaton from `stdin` (HOA expected) and writes the result to `stdout`.

    $ ltl2tgba -B "F G (a | X b)" | ./Tools/owl-1.0.0/bin/nba2ldba

## History

owl is a merger of the previous seperate projects owl-base and owl-translations. owl-base is a merger of the previous seperate projects ltl-lib and omega-automaton-lib. Parts of the code originated from Rabinizer.
