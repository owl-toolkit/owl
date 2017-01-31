# OWL 

[![build status](https://gitlab.lrz.de/i7/owl/badges/master/build.svg)](https://gitlab.lrz.de/i7/owl/commits/master)

## Translations

A set of tools to translate LTL and NBA to LDBA, Parity Automata and Parity Game Arenas.

## Naming

OWL = Omega Word and automata Library.

## Building & Running

1. Build the distribution `./gradlew assemble` (all dependencies are downloaded automatically)
2. The complete toolset can be found packaged in the directory `build/distributions` 

## Library

A simple library for working with omega-automata and linear temporal logic (LTL) written in Java.

## Building 

The following two commands build and install the library into the local maven cache.

    ./gradlew build
    ./gradlew install

## History

owl is a merger of the previous seperate projects owl-base and owl-translations.
owl-base is a merger of the previous seperate projects ltl-lib and omega-automaton-lib.
