# OWL 

[![build status](https://gitlab.lrz.de/i7/owl/badges/master/build.svg)](https://gitlab.lrz.de/i7/owl/commits/master)

[Website](https://www7.in.tum.de/~sickert/projects/owl/)

## About

Owl (**O**mega **W**ord and automata **L**ibrary) is a library tailored for &mdash; but not limited to &mdash; semantic-based translations from LTL to deterministic automata.
It ships basic building blocks for constructing omega-automata and includes several command-line tools implementing these translations.

## Building

The Java command line tools are built with the following command.
All Java dependencies are downloaded automatically.

```
$ ./gradlew buildBin
```

The tools are located in `build/bin`.

To build the included C and C++ tools, an appropriate compiler is required.
A full build can be executed by

```
$ ./gradlew assemble
```

All resulting artifacts are located in `build/distributions`.

By default, ProGuard is used to minimize the resulting jar.
Specify `-Pfull` to disable the optimizations.

## Dedicated Tools

Owl comes with a wide variety of dedicated tools, listed below.
Most tools provide some help which is displayed in case of an parsing error or when calling the tool with `--help` as only argument.

 * `ltl2dgra`: LTL to DGRA translation, based on the Rabinizer construction
   * `--complete`: Construct a complete automaton (i.e., add the `false` state in the master automaton)
   * `--noacceptance`: Don't compute the acceptance information, only construct the state space
   * `--noeager`: Disable eager optimization
   * `--nosuspend`: Disable detection of suspendable subformulas
   * `--nosuspend`: Disable support based relevant formula analysis
 * `ltl2ldba`: LTL to LDBA translation
   * `--degeneralise`: Construct a Büchi automaton instead of a generalized one
   * `--epsilon`: Keep epsilon edges - this yields non-valid HOA output, since epsilon edges are not supported
   * `--guess-f`: Guess which F-operators hold infinitely often
   * `--non-deterministic`: Construct a non-deterministic initial component
   * `--simple`: Construct a simpler state space
 * `ltl2dpa`: LTL to DPA translation, based on either Rabinizer+IAR or LDBA
   * Rabinizer: See options of `ltl2dgra`
   * LDBA: See options of `ltl2dpa`
 * `delag`: LTL to DELA translation, based on a dependency tree construction
   * `--fallback`: Fallback for leafs outside of the supported fragment.
     Specify `none` for strict mode (i.e., fail for unsupported formulas) or some external translator (the string `%F` will be replaced by the actual formula).
     By default, the LDBA based DPA construction is used.
 * `nba2ldba`: NBA to LDBA translation
   * No options
 * `nba2dpa`: NBA to DPA translation, based on the LDBA construction
   * No options
 * `dra2dpa`: DRA to DPA translation, based on the index appearance record construction
   * No options

The type abbreviations mean the following:

 * LTL: Linear Temporal Logic (parsed according to the grammar described in `LTL_GRAMMAR`)
 * NBA: Non-deterministic Büchi Automaton
 * DGRA: Deterministic generalized Rabin Automaton
 * DRA: Deterministic Rabin Automaton
 * LDBA: Limit-deterministic Büchi Automaton
 * DPA: Deterministic Parity Automaton
 * DELA: Deterministic Emerson-Lei Automaton

For a more detailed explanation of each tool, refer to the javadoc of the respective package in `owl.translations`.

#### Options

Each tool accepts specific command line options, which can be listed via `--help`.
Additionally, the following set of common options is understood by all tools.
Due to implementation details, grouping of the options is necessary, i.e. all global options have to be specified first, followed by all input options, and finally tool-specific options can be given.

Global options:
 * `--annotations`: Gather additional, human-readable information where possible.
   For example, the `ltl2ldba` and `ltl2dgra` constructions will gather a readable representation of the semantic state labels created by the construction.
 * `--parallel`: Enable parallel processing where supported.
   As of now, this only has very limited impact, since most of the time BDD operations need to be synchronized, which is tedious to implement both correct and efficiently.
 * `-i INPUT`: Pass `INPUT` as input to the tool
 * `-I FILE`: Pass the contents of `FILE` to the tool
 * `-O FILE`: Write the output to `FILE`
 * `-w count`: Use `count` workers to process multiple inputs in parallel.
   Specify `-1` to use all available processors and `0` for blocking, direct execution.

Additionally, as soon as an unmatched argument is encountered, this and all following arguments will be interpreted as input.
For example, `ltl2dpa "F G a"` is equivalent to `ltl2dpa -i "F G a"`.

## Extended command line syntax

To give full control over the translation process to the user, owl offers a verbose, modular way of specifying a particular toolchain.
This is achieved by the means of multiple building blocks, namely input readers, transformers, and output writers, all of which are pluggable and extendable.

The  three blocks are, as their names suggest, responsible for reading / parsing input, applying operations to objects, and serializing the results, respectively.
For example, we chain an LTL parser to the ltl2dpa construction, followed by (parity) minimization and HOA output by 

```
owl --- ltl --- ltl2dpa --- minimize-aut --- hoa
```

To read from some file `input.ltl` and write to `output.hoa`, we simply have to change the parameters of the coordinator to

```
owl -I "input.ltl" -O "output.hoa" --- ltl --- ltl2dpa --- minimize-aut --- hoa
```

Now, suppose we want to first pre-process the LTL formula.
To this end, we simply add another transformer to the pipeline as follows.

```
owl --- ltl --- simplify-ltl --- ltl2dpa --- minimize-aut --- hoa
```

For research purposes, we might be interested in what exactly happens during the intermediate steps, for example how the rewritten formula looks like, or how large the automaton is prior to the minimization.
These values could be obtained by running different configurations, but this is rather cumbersome.
Instead, we offer the possibility of seamlessly collecting meta-data during the execution process.
For example, to obtain the above information in one execution, we write

```
owl --- ltl --- simplify-ltl --- string --- ltl2dpa --- aut-stat --format "States: %S SCCs: %C Acc: %A" --- minimize-aut --- hoa
```

Owl will now output the requested information together with the corresponding input to stderr (by default).

Often, one might not only be interested in how the existing operations performs, but rather how a new implementation behaves.
By simply delegating to an external translator, existing implementations can easily be integrated in such a pipeline.
For example, to delegate translation to the old Rabinizer 3.1, we could simply write

```
owl --- ltl --- simplify-ltl --mode modal-iter --- unabbreviate -r -w -m --- ltl2aut-ext --tool "java -jar rabinizer3.1.jar -format=hoa -silent -out=std %f" --- minimize-aut --- hoa
```

This command line can easily be extended, see the developers section.

## (Some) Publications

 * Zuzana Komárková, Jan Křetínský: 
   Rabinizer 3: Safraless translation of LTL to small deterministic automata. ATVA 2014

 * Salomon Sickert, Javier Esparza, Stefan Jaax, Jan Kretínský: 
   Limit-Deterministic Büchi Automata for Linear Temporal Logic. CAV 2016

 * Javier Esparza, Jan Křetínský, Jean-François Raskin, Salomon Sickert:
   From LTL and Limit-Deterministic Büchi Automata to Deterministic Parity Automata. TACAS 2017

 * Jan Křetínský, Tobias Meggendorfer, Clara Waldmann, Maximilian Weininger:
   Index appearance record for transforming Rabin automata into parity automata. TACAS 2017

## History

owl is a merger of the previous separate projects owl-base and owl-translations.
owl-base itself was a merger of the previous separate projects ltl-lib and omega-automaton-lib.
The Rabinizer implementation in the code originated from the Rabinizer3.1 implementation.

## Extending Owl

See `CONTRIBUTING` for contribution and style guidelines (mandatory if you want your changes to be merged into the main branch).
Read the javadoc of the respective packages of the infrastructure you plan to use, e.g., `owl.automaton`.
It contains links to the relevant classes and typical use cases.