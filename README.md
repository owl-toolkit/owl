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

To use Owl as a maven library for other projects, install the jar into the maven cache by executing

```
$ ./gradlew install
```

To build the included C and C++ tools, an appropriate compiler is required.
A full build can be executed by

```
$ ./gradlew assemble
```

All resulting artifacts are located in `build/distributions`.  

## Tool Guide

Owl comes with a wide variety of dedicated tools and an extensible command line interface, which is explained later.
Most tools provide some help which is displayed in case of an parsing error or when calling the tool with `--help` as only argument

### Dedicated Tools

These tools are named `x2y`, where `x` denotes the input type (e.g., `ltl`) and `y` the output type (e.g., `dgra`).
The following table summarizes the existing tools.

<table>
  <tr>
    <th>x2y</th>
    <th>dgra</th>
    <th>dra</th>
    <th>ldba</th>
    <th>dpa</th>
  </tr>
  <tr>
    <th>ltl</th>
    <td align="center">x</td>
    <td align="center">x</td>
    <td align="center">x</td>
    <td align="center">x</td>
    </tr>
  <tr>
    <th>nba</th>
    <td></td>
    <td></td>
    <td align="center">x</td>
    <td align="center">x</td>
  </tr>
  <tr>
    <th>dgra</td>
    <td></td>
    <td align="center">x</td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <th align="center">dra</td>
    <td></td>
    <td></td>
    <td></td>
    <td align="center">x</td>
  </tr>
</table>

The type abbreviations mean the following:

 * LTL: Linear Temporal Logic (parsed according to the grammar described in `LTL_GRAMMAR`)
 * NBA: Non-deterministic Büchi Automaton
 * DGRA: Deterministic generalized Rabin Automaton
 * DRA: Deterministic Rabin Automaton
 * LDBA: Limit-deterministic Büchi Automaton  
 * DPA: Deterministic Parity Automaton

#### Options

Each tool accepts specific command line options, which can be listed via `--help`.
Additionally, the following set of common options is understood by all tools.
Due to implementation details, grouping of the options is necessary, i.e. all global options have to be specified first, followed by all input options, and finally tool-specific options can be given.

Global options:
 * `--annotations`: Gather additional, human-readable information where possible.
   For example, the `ltl2ldba` and `ltl2dgra` constructions will gather a readable representation of the semantic state labels created by the construction.
 * `--parallel`: Enable parallel processing where supported. As of now, this only has very limited impact, since most of the time BDD operations need to be synchronized, which is tedious to implement both correct and efficiently. 

Input options:
 * `-i INPUT`: Pass `INPUT` as input to the tool
 * `-I FILE`: Pass the contents of `FILE` to the tool
 * `-O FILE`: Write the output to `FILE`

Additionally, any unmatched arguments will be interpreted as input, i.e. `ltl2dpa "F G a"` is equivalent to `ltl2dpa -i "F G a"`.

### Extended command line syntax

To give full control over the translation process to the user, owl offers a verbose, modular way of specifying a particular toolchain.
This is achieved by the means of multiple building blocks, namely input readers, transformers, and output writers, all of which are pluggable and extendable. Usually, users will be content with reading from standard input or a file.

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
owl --- ltl --- rewrite --mode modal-iter --- ltl2dpa --- minimize-aut --- hoa
```

For research purposes, we might be interested in what exactly happens during the intermediate steps, for example how the rewritten formula looks like, or how large the automaton is prior to the minimization. These values could be obtained by running different configurations, but this is rather cumbersome.
Instead, we offer the possibility of seamlessly collecting meta-data during the execution process.
For example, to obtain the above numbers in one execution, we write

```
owl --- ltl --- rewrite --mode modal-iter --- string --- ltl2dpa --- aut-stat --format "States: %S SCCs: %C Acc: %A" --- minimize-aut --- hoa
```

Owl will now output the requested information together with the corresponding input to stderr (by default).

Often, a researcher might not only be interested in how the existing operations performs, but rather how a new implementation behaves. By simply delegating to an external translator, existing implementations can easily be integrated in such a pipeline. For example, to delegate translation to the old Rabinizer 3.1, we could simply write

```
owl --- ltl --- rewrite --mode modal-iter --- unabbreviate -r -w -m --- ltl2aut-ext --tool "java -jar rabinizer3.1.jar -format=hoa -silent -out=std %f" --- minimize-aut --- hoa
```

The real strength of the implementation comes from its flexibility.
The command-line parser is completely pluggable and written without explicitly referencing any of our implementations. To add a new algorithm to the pipeline, one simply has to provide a name (as, e.g., ltl2nba), an optional set of command line options and a way of obtaining the configured translator from the parsed options.
For example, supposing that this new ltl2nba command should have some `--fast` flag, the whole description necessary is as follows: 

```java
TransformerSettings settings = ImmutableTransformerSettings.builder()
  .key("ltl2nba")
  .optionsDirect(new Options()
    .addOption("f", "fast", false, "Turn on ludicrous speed!"))
  .transformerSettingsParser(settings -> {
    boolean fast = settings.hasOption("fast");
    return environment -> (input, context) -> LTL2NBA.apply((LabelledFormula) input, fast, environment);
  }).build();
```

After registering these settings, the tool can now be used exactly as ltl2dpa before.
Parsers, serializers or even coordinators can be registered with the same kind of specification.
Similarly, dedicated command line tools like our presented `ltl2dgra` or `nba2dpa` can easily be created by delegating to this generic framework. 

## Publications

 * Zuzana Komárková, Jan Křetínský: 
   Rabinizer 3: Safraless translation of LTL to small deterministic automata. ATVA 2014

 * Salomon Sickert, Javier Esparza, Stefan Jaax, Jan Kretínský: 
   Limit-Deterministic Büchi Automata for Linear Temporal Logic. CAV 2016

 * Javier Esparza, Jan Křetínský, Jean-François Raskin, Salomon Sickert:
   From LTL and Limit-Deterministic Büchi Automata to Deterministic Parity Automata. TACAS 2017

 * Jan Křetínský, Tobias Meggendorfer, Clara Waldmann, Maximilian Weininger:
   Index appearance record for transforming Rabin automata into parity automata. TACAS 2017

## History

owl is a merger of the previous separate projects owl-base and owl-translations. owl-base itself was a merger of the previous separate projects ltl-lib and omega-automaton-lib.
The Rabinizer implementation in the code originated from the Rabinizer3.1 implementation.
