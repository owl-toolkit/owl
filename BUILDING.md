# Build Instructions

### Native Distribution

Download 'GraalVM 21.2: JDK 11' from the [GraalVM website](https://www.graalvm.org/) and install it with the **native-image** component.
Do not forget to add the installed tools to the search path:

```
PATH=/<path>/graalvm-ce-java11-21.0.0.2/bin/:$PATH
JAVA_HOME=/<path>/graalvm-ce-java11-21.0.0.2/
```

Then execute the following command to obtain the native-image distribution:

```
./gradlew nativeImageDistZip -Pdisable-pandoc
```

The resulting `.zip` is located in `build/distributions`.

### JRE Distribution

If GraalVM cannot be installed, the JRE-distribution can be built with:

```
./gradlew distZip -Pdisable-pandoc
```

The resulting `.zip` is located in `build/distributions`.

### Docker

In case you want to build or test locally using docker (recommended on Windows), first build the docker image with
```
  cd docker-build-environment && docker build -t owl .
```
Then, run the build process via
```
  docker run --rm -it -v <path>/owl:/dir -w /dir owl ./gradlew build
```
or similar. Optionally, you can pass `GRADLE_HOME=./.gradle` and `GRADLE_USER_HOME=./.gradle` to speed up subsequent builds.

# Development Instructions

To test locally, run `gradle localEnvironment` to update the folder and then `python scripts/util.py test <name>` to run the respective test.
On Windows, installing WSL is required and the testing commands (scripts in the `script` folder) should be run from within WSL.
To test inside docker, run

```
  docker run -e GRADLE_HOME=./.gradle -e GRADLE_USER_HOME=./.gradle --rm -it -v <path>/owl:/dir -w /dir owl ./gradlew localEnvironment && python scripts/util.py test <name>
```

## Setup

A working Intelji IDEA development environment can be obtained by `./gradlew idea` and then importing the generated project (`owl.ipr`) to IDEA.
If you instead want to work with the IDEA Gradle plugin, import the project as usual ("Open Project" > `build.gradle`) and perform the following configuration:

 * "Code Style": Import from `config/idea-codestyle.xml`
 * "Inspections": Import from `config/idea-inspection-profile.xml`
 * "Dictionary": Add from `config/dictionary.dic`

## Checks

Before submitting code please executed `./gradlew check` locally to run all code checks.
Apart from jUnit tests, static code analysis is performed using [PMD](https://pmd.github.io/) and [ErrorProne](http://errorprone.info/) (rules are located in the `config` folder).
Further, [checkstyle](http://checkstyle.sourceforge.net/) is used to check compliance with the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
Passing all these tests is mandatory for submitted code.

## Coding Conventions

### Collections and Streams

Owl currently targets Java 11 and thus `Stream` can be used.
However, for performance-critical code it might be better to replace them by equivalent `for`-loops.
It is recommended to use immutable collection for fields that do not require mutation.
Depending on your use-case make use of `List.of`, `List.copyOf`, `Set.of`, `Set.copyOf`, `Map.of`, or `Map.copyOf`.
If your use case is not covered by one of JDK collections you can make use of Guava's collections or Owl's own `Collections3` and `BitSet2`.

### Records

For classes that are data classes, i.e., named tuples, [AutoValue](https://github.com/google/auto/tree/master/value) should be used.
This is only an intermediate solution until Java 16 is supported by GraalVM.

### Exceptions and Validation

 * Use `assert` and Guavas `Precondition` methods frequently, it drastically simplifies debugging and even can help reading code.
   Usually, asserts should only be used for internal consistency checks, i.e. double-checking your own implementation.
   For owl, `assert` can also be used to check arguments if the check is extremely costly or in a very critical code section.
 * Always provide some useful message for exceptions, since this will be printed to the user.

The following conventions should be followed:

 * If some operation is currently not implemented:
   * `UnsupportedOperationException`: The operation is not supported yet, but this could change in the future.
   * `IllegalArgumentException`: This operation will never be supported (for the given input).

### Naming

In general, everything should have self-explanatory, english names.
Also, the following naming schemes should be followed:

 * formulas (instead of formulae)
 * -ize (instead of -ise)

### Sorting

Members of a class can be sorted alphabetically (adhering to the general order), but a logical structure is preferred.
For example, one can keep setters and getter together or group methods by their type (e.g., simple property accessor, complex mutation, ...).
For tuples (see above), the convention is the following: "fields", derived fields, factory methods ("`of`"), constructor-like methods ("`with...`"), other methods.

### Javadoc

Javadoc is not required everywhere, but strongly encouraged.
Code without any documentation is not accepted.
The [Oracle](http://www.oracle.com/technetwork/java/javase/tech/index-137868.html) and [Google](https://google.github.io/styleguide/javaguide.html#s7-javadoc) style guides apply.
Specifically, block tags (like `@throws`, `@return`, etc.) should be continued by a lower-case sentence.
For example, `@throws IllegalArgumentException if the argument is not allowed` or `@return the thing`.

## Developing on Windows

To use development tools on Windows:

* Install Cygwin and Python
* Compile current version of spot
* (optional) `dos2unix scripts/*.sh` if your git checkout changes newlines
* Now, `ltlcross-run.sh` etc. should work (inside the Cygwin console)

## Dependency verification

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
