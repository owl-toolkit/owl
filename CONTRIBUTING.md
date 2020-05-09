# Setup

A working Intelji IDEA development environment can be obtained by `./gradlew idea` and then importing the generated project (`owl.ipr`) to IDEA.

If you instead want to work with the IDEA Gradle plugin, import the project as usual ("Open Project" > `build.gradle`) and perform the following configuration:

 * "Code Style": Import from `config/idea-codestyle.xml`
 * "Inspections": Import from `config/idea-inspection-profile.xml`
 * "Dictionary": Add from `config/dictionary.dic`


# Checks

Before submitting code please executed `./gradlew check` locally to run all code checks.
Apart from jUnit tests, static code analysis is performed using [PMD](https://pmd.github.io/) and [ErrorProne](http://errorprone.info/) (rules are located in the `config` folder).
Further, [checkstyle](http://checkstyle.sourceforge.net/) is used to check compliance with the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
Passing all these tests is mandatory for submitted code.


# Coding Conventions

In general, features of Java 11, like Lambdas, Streams, `Collections.of`, `forEach`-style, etc., can and should be used frequently.
Streams should only be used for prototypes or in non-performance critical code, since they do add noticeable overhead.
Typical "best practises" like KISS and DRY should be adhered to, some performance can be sacrificed for clear and concise code.
Nevertheless, performance must not be neglected.

## Exceptions and Checks

 * Use `assert` and Guavas `Precondition` methods frequently, it drastically simplifies debugging and even can help reading code.
   Usually, asserts should only be used for internal consistency checks, i.e. double-checking your own implementation.
   For owl, `assert` can also be used to check arguments if the check is extremely costly or in a very critical code section.
 * Always provide some useful message for exceptions, since this will be printed to the user.

The following conventions should be followed:

 * If some operation is currently not implemented:
   * `UnsupportedOperationException`: The operation is not supported yet, but this could change in the future.
   * `IllegalArgumentException`: This operation will never be supported (for the given input).

## Utilities

Where applicable, JDK functionality should be preferred (e.g., JDK `List.of` over Guava `ImmutableList.of`).
If the JDK does not offer some particular functionality, Guava usually provides it and that should be used.
Before implementing your own methods, double check that none of owl's utility libraries provide it already.

 * See `Collections3` for some specific collection methods.
 * Where applicable, use the primitive versions of collections provided by `fastutil`.
 * For anything related to natural numbers, see `naturals-util` for more specific implementations (e.g., index maps).

## Immutable tuples

For classes which are nothing more than immutable structs, i.e. data containers / tuples, [Immutables](http://immutables.github.io/) should be used.
See `@Tuple` / `@HashedTuple` and their usages for examples.
Hiding the implementation class (by, e.g., giving it package visibility) is recommended, since Java may support value types natively in the near future.
Finally, for performance critical code, double-check that the generated code is doing what you expect from it (e.g., no superfluous copies are performed).

## Naming

In general, everything should have self-explanatory, english names.
Also, the following naming schemes should be followed:

 * formulas (instead of formulae)
 * -ize (instead of -ise)

## Sorting

Members of a class can be sorted alphabetically (adhering to the general order), but a logical structure is preferred.
For example, one can keep setters and getter together or group methods by their type (e.g., simple property accessor, complex mutation, ...).
For tuples (see above), the convention is the following: "fields", derived fields, factory methods ("`of`"), constructor-like methods ("`with...`"), other methods.

## Javadoc

Javadoc isn't required everywhere, but strongly encouraged.
Code without any documentation is not accepted.
The [Oracle](http://www.oracle.com/technetwork/java/javase/tech/index-137868.html) and [Google](https://google.github.io/styleguide/javaguide.html#s7-javadoc) style guides apply.
Specifically, block tags (like `@throws`, `@return`, etc.) should be continued by a lower-case sentence.
For example, `@throws IllegalArgumentException if the argument is not allowed` or `@return the thing`.

## Windows

To use development tools on Windows:

* Install Cygwin and Python
* Compile current version of spot
* Manually compile owl-client at `src/main/c` and place it in `build/bin`
* (optional) `dos2unix scripts/*.sh` if your git checkout changes newlines
* Now, `ltlcross-run.sh` etc. should work (inside the Cygwin console)

# Dependency verification

When changing dependency versions, run `gradlew --write-verification-metadata sha256 help` to regenerate the checksums.
Currently `junit-bom.pom` has to be added manually due to a bug.

To completely regenerate the file and prune stale entries, delete the file and run above command.
Then, add
```
<trusted-artifacts>
 <trust file=".*-javadoc[.]jar" regex="true"/>
 <trust file=".*-sources[.]jar" regex="true"/>
</trusted-artifacts>
```
to the `<configuration>` section, otherwise IntelliJ complains.