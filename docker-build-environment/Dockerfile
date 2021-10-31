FROM ubuntu:20.04

MAINTAINER Salomon Sickert

ENV LANG C.UTF-8

# Remember:
# - Prefer LTS releases for build images in order to be compatible with more
#   systems in case of dynamic linking of glibc.
# - Most commands are chained in order to reduce the number of created
#   layers and hence the size of the final image (before compression by GitLab).

# Base System

RUN apt-get -q update \
    && apt-get -q upgrade -y \
    && apt-get -q install -y --no-install-recommends build-essential libz-dev zlib1g-dev ca-certificates pandoc python3 python3-tabulate graphviz time gcc g++ make unzip wget \
    && rm -rf /var/lib/apt/lists/*


# Musl

## Download, verify, unpack, compile, install, and test.

RUN wget -q https://musl.libc.org/releases/musl-1.2.2.tar.gz \
    && echo '9b969322012d796dc23dda27a35866034fa67d8fb67e0e2c45c913c3d43219dd musl-1.2.2.tar.gz' | sha256sum --check \
    && tar -zxvf musl-1.2.2.tar.gz \
    && rm musl-1.2.2.tar.gz \
    && cd musl-1.2.2 \
    && ./configure --disable-shared \
    && make -j 4 \
    && make install \
    && cd .. \
    && rm -rf musl-1.2.2 \
    && /usr/local/musl/bin/musl-gcc --version

ENV PATH=/usr/local/musl/bin/:$PATH


# zlib

## Download, verify, unpack, compile, and install.

RUN wget -q https://zlib.net/zlib-1.2.11.tar.gz \
    && echo 'c3e5e9fdd5004dcb542feda5ee4f0ff0744628baf8ed2dd5d66f8ca1197cb1a1 zlib-1.2.11.tar.gz' | sha256sum --check \
    && tar -zxvf zlib-1.2.11.tar.gz \
    && rm zlib-1.2.11.tar.gz \
    && cd zlib-1.2.11 \
    && CC=musl-gcc ./configure --static --prefix=/usr/local/musl \
    && CC=musl-gcc make \
    && CC=musl-gcc make install


# GraalVM

## Download, verify, unpack, install, and test.

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java17-linux-amd64-21.3.0.tar.gz \
    && echo '11d8039e0a7a31b799a6f20a0e806e4128730e9a2595a7ffdec1443539d4c3f6 graalvm-ce-java17-linux-amd64-21.3.0.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java17-linux-amd64-21.3.0.tar.gz \
    && rm graalvm-ce-java17-linux-amd64-21.3.0.tar.gz \
    && mv graalvm-ce-java17-21.3.0 /opt/

ENV PATH=/opt/graalvm-ce-java17-21.3.0/bin/:$PATH
ENV JAVA_HOME=/opt/graalvm-ce-java17-21.3.0/

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/native-image-installable-svm-java17-linux-amd64-21.3.0.jar \
    && echo 'df488a04b5405c6443c90e94710cd3bd2be9adcb3768f91429aa494168d52440 native-image-installable-svm-java17-linux-amd64-21.3.0.jar' | sha256sum --check \
    && gu -L install native-image-installable-svm-java17-linux-amd64-21.3.0.jar \
    && rm native-image-installable-svm-java17-linux-amd64-21.3.0.jar \
    && java -version \
    && native-image --version


# Spot

## Download, verify, unpack, compile, and install

RUN wget -q https://www.lrde.epita.fr/dload/spot/spot-2.9.3.tar.gz \
    && echo '02a348272329d4931905a586f7197b518ac6fa411aeeea2acac55d6d490ecd1e spot-2.9.3.tar.gz' | sha256sum --check \
    && tar -zxvf spot-2.9.3.tar.gz \
    && rm spot-2.9.3.tar.gz \
    && cd spot-2.9.3 \
    && ./configure --disable-python --enable-max-accsets=1024 \
    && make -j 4 \
    && make install \
    && cd .. \
    && rm -rf spot-2.9.3

ENV LD_LIBRARY_PATH=/usr/local/lib/:$LD_LIBRARY_PATH


# Print Configuration

RUN musl-gcc --version \
    && java -version \
    && native-image --version \
    && ltlcross --version
