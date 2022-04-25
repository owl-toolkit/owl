# Contributing to Owl

:+1::tada: First off, thanks for taking the time to contribute! :tada::+1:

The following is a set of guidelines for contributing to Owl, which are hosted in
the [Owl Repository](https://github.com/owl-toolbox/owl) on GitHub. These are mostly guidelines, not
rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

## Reporting Bugs

Bugs are tracked as GitHub issues. If you encounter a problem using Owl please create an issue and
explain the problem and include additional details to help maintainers reproduce the problem:

* Which version of Owl are you using? You can get the exact version by running `owl --version`.
* What's the name and version of the OS you're using?
* How much memory is available?
* Are there any stacktraces?
* Can you provide inputs that trigger the problem?

## Contributing Code

Code contributions are welcomed and expected in the form of pull requests. Small contributions,
e.g., fixes to the build systems, documentation changes, local and small code changes, are always
welcome. If you plan to contribute larger changes, e.g. a new construction, please contact the
maintainers first.

Please follow the guidance on developing code for Owl below when submitting changes.

## Building and Developing

For instructions on how to build Owl please see [BUILDING.md](BUILDING.md).

It is recommended to work with Intellij IDEA on the code. A development environment can be obtained
by using the Gradle project importer ("Open Project" > `build.gradle`) and perform the following
additional steps:

* "Code Style": Import from `config/intellij-java-google-style.xml`
* "Dictionary": Import from `config/dictionary.dic`

### Code Style

The Owl project adheres to
the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). Please make sure
that yor code is correctly formated before submitting changes.

### Copyright Notice

Please add (or update) the following header in all files that are part of the contribution. If your
change is minor, e.g., renaming of a method, small fix, then leave the list of authors unchanged.

```
/*
 * Copyright (C) (year the file was created), (current year)  (author list of the file)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
```

### Coding Conventions

#### Collections and Streams

Owl currently targets Java 17 and thus `Stream` can be used. However, for performance-critical code
it might be better to replace them by equivalent `for`-loops. It is recommended to use immutable
collection for fields that do not require mutation. Depending on your use-case make use of `List.of`
, `List.copyOf`, `Set.of`, `Set.copyOf`, `Map.of`, or `Map.copyOf`. If your use case is not covered
by one of JDK collections you can make use of Guava's collections or Owl's own `Collections3`
, `BitSet2`, and `ImmutableBitSet`. You can find specialised datastructures and algorithms in
the `owl.collections` package.

| Discouraged                           | Replacement                               |
|---------------------------------------|-------------------------------------------|
| `Collectors.toList()`                 | `Collectors.toCollection(ArrayList::new)` |
| `Collectors.toUnmodifiableList()`     | `Stream.toList()`                         |
| `Collections.empty{Set,List,Map}`     | `Set.of()`, `List.of()`, `Map.of()`       |
| `Collections.singleton{Set,List,Map}` | `Set.of(..)`, `List.of(..)`, `Map.of(..)` |

#### Records

For classes that are data classes, i.e., named tuples, Java records should be used.

#### Exceptions and Validation

* Use `assert` and Guavas `Precondition` methods frequently, it simplifies debugging and
  even helps reading code. Usually, asserts should only be used for internal consistency checks,
  i.e. double-checking your own implementation. For owl, `assert` can also be used to check
  arguments if the check is extremely costly or in a very critical code section.
* Always provide some useful message for exceptions, since this will be printed to the user.

#### Naming

In general, everything should have self-explanatory, english names. Also, the following naming
schemes should be followed:

* formulas (instead of formulae)
* -ize (instead of -ise)

#### Sorting

Members of a class can be sorted alphabetically (adhering to the general order), but a logical
structure is preferred. For example, one can keep setters and getter together or group methods by
their type (e.g., simple property accessor, complex mutation, ...).

#### Javadoc

Javadoc is not required everywhere, but strongly encouraged. Code without any documentation is not
accepted. The [Oracle](http://www.oracle.com/technetwork/java/javase/tech/index-137868.html)
and [Google](https://google.github.io/styleguide/javaguide.html#s7-javadoc) style guides apply.
Specifically, block tags (like `@throws`, `@return`, etc.) should be continued by a lower-case
sentence. For example, `@throws IllegalArgumentException if the argument is not allowed`
or `@return the thing`.

## Dependency verification

When changing dependency versions, run `gradlew --write-verification-metadata sha256 help` to
regenerate the checksums. Currently `junit-bom.pom` has to be added manually due to a bug.

To completely regenerate the file and prune stale entries, delete the file and run above command.
Then, add

```
<trusted-artifacts>
 <trust file=".*-javadoc[.]jar" regex="true"/>
 <trust file=".*-sources[.]jar" regex="true"/>
</trusted-artifacts>
```

to the `<configuration>` section, otherwise IntelliJ rejects these files.

## Development Instructions

To test Owl, run `gradle localEnvironment` to update the folder and
then `python3 scripts/util.py test <name>` to run the respective test. On Windows, installing WSL is
required and the testing commands (scripts in the `script` folder) should be run from within WSL. To
test inside docker, run

```
  docker run -e GRADLE_HOME=./.gradle -e GRADLE_USER_HOME=./.gradle --rm -it -v <path>/owl:/dir -w /dir owl ./gradlew localEnvironment && python scripts/util.py test <name>
```