# Owl

A tool collection and library for <b>O</b>mega-<b>w</b>ords, ω-automata and <b>L</b>inear Temporal Logic (LTL). Batteries included.

Functionality (e.g., translations, simplifiers) is available through command-line tools and a Java and C-API. Details on how to use Owl are given in the [usage instructions](doc/USAGE.md). If you want to contribute to Owl, read the [contribution guidelines](CONTRIBUTING.md) which are mandatory if you want your changes to be merged into the main branch.
Read the javadoc of the respective packages of the infrastructure you plan to use, e.g., `owl.automaton`. It contains links to the relevant classes and typical use cases.

For further information see the official [website](https://owl.model.in.tum.de/).

## Prerequisites

Building the project from the repository requires [GraalVM 20.1: JDK 11](https://www.graalvm.org/), a C build environment with the `glibc` and `zlib` header files installed, and [pandoc](https://pandoc.org/). On Ubuntu the required dependencies can be installed via the following commands:

```
$ apt-get -q install -y --no-install-recommends build-essential zlib1g-dev pandoc gcc
$ wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-20.1.0/graalvm-ce-java11-linux-amd64-20.1.0.tar.gz \
    && echo '18f2dc19652c66ccd5bd54198e282db645ea894d67357bf5e4121a9392fc9394 graalvm-ce-java11-linux-amd64-20.1.0.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-20.1.0.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-20.1.0.tar.gz \
    && mv graalvm-ce-java11-20.1.0 /opt/
$ gu install native-image
```

Do not forget to add the installed tools to the search path:

```
PATH=/opt/graalvm-ce-java11-20.1.0/bin/:$PATH
JAVA_HOME=/opt/graalvm-ce-java11-20.1.0/
```

If GraalVM (native-image) is not available, the project can also be built with a reduced set of features on any JDK that supports at least Java 11. See below for instructions.

## Building on GraalVM

The standard distribution can be obtained with:

```
$ ./gradlew distZip
```

The resulting `.zip` is located in `build/distributions`. It includes the scripts for the CLI tools, Jars usable as a Java library, and a C library.

## Building on OpenJDK

If GraalVM is not available, building the native executable and library can be skipped by executing:

```
$ ./gradlew distZip -Pdisable-native
```

## Citing

Please see the [citing section of the official website for an updated list](https://owl.model.in.tum.de/#citing).
