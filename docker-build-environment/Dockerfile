MAINTAINER Salomon Sickert

ENV LANG C.UTF-8

# Remember:
# - Prefer LTS releases for build images in order to be compatible with more
#   systems in case of dynamic linking of glibc.
# - Most commands are chained in order to reduce the number of created
#   layers and hence the size of the final image (before compression by GitLab).

# Tools
FROM ubuntu:20.04 AS tools
RUN apt-get -q update && apt-get install -y git wget g++ gcc libc6-dev libffi-dev libgmp-dev make xz-utils zlib1g-dev git gnupg netbase cmake libhwloc-dev
## Stack
RUN wget -q https://github.com/commercialhaskell/stack/releases/download/v2.5.1/stack-2.5.1-linux-x86_64.tar.gz \
  && echo 'c83b6c93d6541c0bce2175085a04062020f4160a86116e20f3b343b562f2d1e8 stack-2.5.1-linux-x86_64.tar.gz' | sha256sum --check \
  && tar -zxvf stack-2.5.1-linux-x86_64.tar.gz

## Syfco
RUN git clone https://github.com/reactive-systems/syfco.git syfco \
  && cd syfco \
  && git checkout 0f0f6a6fff7960d8a0cbb120587984183fa7fc0e \
  && /stack-2.5.1-linux-x86_64/stack build --copy-bins --local-bin-path /build syfco

## combine-aiger
RUN git clone https://github.com/meyerphi/combine-aiger.git \
    && cd combine-aiger \
    && git checkout 9c8ad816fc7d5c1867b561ccf9b5cfd9141e5b64 \
    && make

## AIGER
RUN git clone https://github.com/arminbiere/aiger.git \
    && cd aiger \
    && git checkout 4f08b8cf56edbaa3730abc5c72bc26bbd2da0d6e \
    && ./configure.sh \
    && make

## Sylvan
RUN git clone https://github.com/trolando/sylvan.git \
    && cd sylvan \
    && git checkout 8fa1f4dcd23143ad2483a1957bf8458b39a7954b \
    && mkdir build \
    && cd build \
    && cmake .. \
    && make

# Base system
FROM ubuntu:20.10
COPY --from=tools /build/syfco /usr/bin
COPY --from=tools /combine-aiger/combine-aiger /usr/bin
COPY --from=tools /aiger/smvtoaig /usr/bin
COPY --from=tools /aiger/aigtoaig /usr/bin
COPY --from=tools /sylvan/build/src/libsylvan.so /usr/lib

RUN apt-get -q update \
    && apt-get -q upgrade -y \
    && apt-get -q install -y --no-install-recommends build-essential libz-dev zlib1g-dev ca-certificates pandoc python3 python3-tabulate graphviz time gcc g++ make unzip wget parallel cmake libboost-dev libboost-program-options-dev libboost-filesystem-dev libboost-iostreams-dev zlib1g-dev openjdk-13-jdk \
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

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.2.0/graalvm-ce-java11-linux-amd64-21.2.0.tar.gz \
    && echo 'bbd3e03025168172a76c2a29e6a14c1c37e3476b30774259c3ef5952fb86f470 graalvm-ce-java11-linux-amd64-21.2.0.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-21.2.0.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-21.2.0.tar.gz \
    && mv graalvm-ce-java11-21.2.0 /opt/

ENV PATH=/opt/graalvm-ce-java11-21.2.0/bin/:$PATH
ENV JAVA_HOME=/opt/graalvm-ce-java11-21.2.0/

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.2.0/native-image-installable-svm-java11-linux-amd64-21.2.0.jar \
    && echo '6fc4c6becbe0331aa0a038660fde7c4769c6d6e97d5a90a716a898188d3a6df9 native-image-installable-svm-java11-linux-amd64-21.2.0.jar' | sha256sum --check \
    && gu -L install native-image-installable-svm-java11-linux-amd64-21.2.0.jar \
    && rm native-image-installable-svm-java11-linux-amd64-21.2.0.jar \
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

# NuSMV
RUN wget -q https://nusmv.fbk.eu/distrib/NuSMV-2.6.0-linux64.tar.gz \
    && echo '11b12e42bc04566c4ec1f7cc205fa8704382ac4d69e052154cbb59c9b7e8e08c NuSMV-2.6.0-linux64.tar.gz' | sha256sum --check \
    && tar -zxvf NuSMV-2.6.0-linux64.tar.gz \
    && rm NuSMV-2.6.0-linux64.tar.gz \
    && mv NuSMV-2.6.0-Linux/lib/* /usr/local/lib \
    && mv NuSMV-2.6.0-Linux/bin/* /usr/bin \
    && rm -rf NuSMV-2.6.0-Linux

# NuXmv
RUN wget -q https://es-static.fbk.eu/tools/nuxmv/downloads/nuXmv-2.0.0-linux64.tar.gz \
    && echo '19ff908008d3af2b198fba93b6dd707103e06a70ed3b462d458e329212cfcd5a nuXmv-2.0.0-linux64.tar.gz' | sha256sum --check \
    && tar -zxvf nuXmv-2.0.0-linux64.tar.gz \
    && rm nuXmv-2.0.0-linux64.tar.gz \
    && mv nuXmv-2.0.0-Linux/bin/* usr/bin \
    && rm -rf nuXmv-2.0.0-Linux

# Print Configuration

RUN musl-gcc --version \
    && java -version \
    && native-image --version \
    && ltlcross --version
