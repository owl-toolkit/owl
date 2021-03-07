# Owl

A tool collection and library for <b>O</b>mega-<b>w</b>ords, ω-automata and <b>L</b>inear Temporal Logic (LTL). Batteries included.

Functionality (e.g., translations, simplifiers) is available through command-line tools, as well as a Java API and a restricted C API.
Details on how to use Owl are given in the [usage instructions](doc/USAGE.md).
If you want to contribute to Owl, read the [contribution guidelines](CONTRIBUTING.md) which are mandatory if you want your changes to be merged into the main branch. Read the javadoc of the respective packages of the infrastructure you plan to use, e.g., `owl.automaton`.
It contains links to the relevant classes and typical use cases.

For further information see the official [website](https://owl.model.in.tum.de/).

## Building

### Prerequisites

#### Linux 64-bit

Building the project from the repository requires [GraalVM 21.0: JDK 11](https://www.graalvm.org/), a C build environment with the `glibc` and `zlib` header files installed, and [pandoc](https://pandoc.org/). On Ubuntu the required dependencies can be installed via the following commands:

```
apt-get -q install -y --no-install-recommends build-essential zlib1g-dev pandoc gcc
wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.0.0.2/graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && echo 'bd3fbe796e803c4fe5cd089371588d3d716fa3cdb653fe8dd6dba31b57bef934 graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && mv graalvm-ce-java11-21.0.0.2 /opt/
gu install native-image
```

Do not forget to add the installed tools to the search path:

```
PATH=/opt/graalvm-ce-java11-21.0.0.2/bin/:$PATH
JAVA_HOME=/opt/graalvm-ce-java11-21.0.0.2/
```

If GraalVM (native-image) is not available, the project can also be built with a reduced set of features on any JDK that supports at least Java 11. See below for instructions.

#### macOS

TBD.

#### Windows 10

TBD.

### GraalVM

The standard distribution can be obtained with:

```
./gradlew distZip
```

The resulting `.zip` is located in `build/distributions`. It includes the scripts for the CLI tools, Jars usable as a Java library, and a C library.

### OpenJDK

If GraalVM is not available, building the native executable and library can be skipped by executing:

```
./gradlew distZip -Pdisable-native
```

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

## Testing

To test locally, run `gradle localEnvironment` to update the folder and then `python scripts/util.py test <name>` to run the respective test.
On Windows, installing WSL is required and the testing commands (scripts in the `script` folder) should be run from within WSL.
To test inside docker, run
```
  docker run -e GRADLE_HOME=./.gradle -e GRADLE_USER_HOME=./.gradle --rm -it -v <path>/owl:/dir -w /dir owl ./gradlew localEnvironment && python scripts/util.py test <name>
```

## Citing

Please see the [citing section of the official website for an updated list](https://owl.model.in.tum.de/#citing).
