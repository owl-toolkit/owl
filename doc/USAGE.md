# Usage and Integration

## Dedicated Tools

`Owl` comes with a wide variety of dedicated tools, an non-exhaustive list is given below.
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
 * `ltl2dpa`: LTL to DPA translation, based on either Rabinizer+IAR or LDBA (select with `--mode`)
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

 * LTL: Linear Temporal Logic
 * NBA: Non-deterministic Büchi Automaton
 * DGRA: Deterministic generalized Rabin Automaton
 * DRA: Deterministic Rabin Automaton
 * LDBA: Limit-deterministic Büchi Automaton
 * DPA: Deterministic Parity Automaton
 * DELA: Deterministic Emerson-Lei Automaton

See the [format descriptions](FORMATS.md) for further details on these types.
For a more detailed explanation of each tool, refer to the javadoc of the respective package in `owl.translations`.

### Options

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

`Owl` comes with a flexible command line interface intended to aid rapid development and prototyping of various constructions, explained here.
To give full control over the translation process to the user, it offers a verbose, modular way of specifying a particular tool-chain.
This is achieved by means of multiple building blocks, which are connected together to create the desired translation.
These "building blocks" come in three different flavours, namely input parsers, transformers, and output writers, all of which are pluggable and extensible.

These three blocks are, as their names suggest, responsible for parsing input, applying operations to objects, and serializing the results to the desired format, respectively.
We refer to a sequence of a parser, multiple transformers and an output writer as "pipeline".

Once configured, a pipeline is passed to an executor, which sets up the input/output behaviour and actually executing the pipeline.
Usually, users will be content with reading from standard input or a file, which is handled by the default executor `owl`.
Other possibilities, like a network server, will be mentioned later.

### Basic usage

This approach is explained through a simple, incremental example.
To begin with, we chain an LTL parser to the `ltl2dpa` construction and output the resulting automaton in the HOA format by

```
% owl  ltl --- ltl2dpa --- hoa
```

Fixed input can be specified with `-i "<input>"`, while `-I "<input.file>"` reads the given file.
Similarly, output is written to a file with `-O "<output.file>"`

To additionally pre-process the input formula and minimize the result automaton, one simply adds more transformers to the pipeline

```
% owl  ltl --- simplify-ltl --- ltl2dpa --- minimize-aut --- hoa
```

For research purposes, one may be interested in what exactly happens during the intermediate steps, for example how the rewritten formula looks like, or how large the automaton is prior to the minimization.
This data could be obtained by executing several different configurations, which is cumbersome and time-consuming for large data-sets.
Instead, a seamless meta-data collection during the execution process is offered.
For example, to obtain the above numbers in one execution, write

```
% owl  ltl --- simplify-ltl --- string --- ltl2dpa --- aut-stat --format "%S/%C/%A" --- minimize-aut --- hoa
```

`Owl` will now output the rewritten formula plus the amount of states, number of SCCs and number of acceptance sets for each input to stderr (by default).

### Extending the Framework

Often, a researcher might not only be interested in how the existing operations performs, but rather how a new implementation behaves.
By simply delegating to an external translator, existing implementations can easily be integrated in such a pipeline.
For example, translation can be delegated to Rabinizer 3.1 by

```
% owl  ltl --- simplify-ltl --- ltl2aut-ext --tool "run-rabinizer.sh %f" --- minimize-aut --- hoa
```

The real strength of this framework comes from its flexibility.
The command-line parser is completely pluggable and written without explicitly referencing any implementation.
In order to add a new algorithm, one simply has to provide a name (as, e.g., `ltl2nba`), an optional set of command line options and a way of obtaining the configured translator from the parsed options.
For example, to add a new construction called `ltl2nba` with a `--fast` flag, the whole description necessary is as follows:

```java
public static final TransformerParser CLI_SETTINGS = ImmutableTransformerParser.builder()
    .key("ltl2nba")
    .description("Translates LTL to NBA really fast")
    .optionsDirect(new Options()
      .addOption("f", "fast", false, "Turn on fast mode"))
    .parser(settings -> {
      boolean fast = settings.hasOption("fast");
      return env -> (input, context) ->
        LTL2NBA.apply((LabelledFormula) input, fast, env))
    .build();
\end{verbatim}
```

After registering these settings with a one-line call, the tool can now be used exactly as `ltl2dpa` before.
Additionally, the tool is automatically integrated into the `--help` output of `Owl`, without requiring further interaction from the developer.
Parsers and serializers can be registered with the same kind of specification.

### Advanced Usage

Some advanced features are:

 * Dedicated tools can easily be created by delegating to the generic framework.
   For example, `ltl2ldba` is created by
   
   ```java
    public static void main(String... args) {
     PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2ldba")
       .reader(InputReaders.LTL)
       .addTransformer(Transformers.LTL_SIMPLIFIER)
       .addTransformer(LTL2LDBACliParser.INSTANCE)
       .writer(OutputWriters.HOA)
       .build());
   }
   ```
   This automatically sets up command line argument processing, input / output parsing, help printing, etc.

 * The *server mode* listens on a given address and port for incoming TCP connections.
   Each of these connections then is handled as a separate pair of input source / output sink, i.e. the specified input parser reads from each connection and the resulting outputs are written back to the client, all completely transparent to the translation modules.
   For example, a `ltl2dpa` server is started by writing
   
   ```
   % owl-server  ltl --- simplify-ltl --- ltl2dpa --- hoa
   ```

   Sending input is as easy as `nc localhost 5050` and starting to type.
   We also provide a small C utility `owl-client` dedicated to this purpose for users without access to `netcat`.
   This allows easy usage as a fast back-end server, since the JVM does not have to start for each input.

 * Extended runners can also be written by users.
   It is entirely possible to, e.g., implement a \enquote{JSON} runner, which reads input from a HTTP request and returns the results together with all meta-information in form of a JSON-file.
