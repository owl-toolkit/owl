FROM ubuntu:20.10

MAINTAINER Salomon Sickert

# Remember: Most commands are chained in order to reduce the number of created
# layers and hence the size of the final image.

# Default to UTF-8 file encoding

ENV LANG C.UTF-8

# Tools
FROM ubuntu:20.10 AS tools
RUN apt-get -q update && apt-get install -y git wget g++ gcc libc6-dev libffi-dev libgmp-dev make xz-utils zlib1g-dev git gnupg netbase
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

# Install base system
FROM ubuntu:20.10
COPY --from=tools /build/syfco /usr/bin
COPY --from=tools /combine-aiger/combine-aiger /usr/bin
COPY --from=tools /aiger/smvtoaig /usr/bin
COPY --from=tools /aiger/aigtoaig /usr/bin

RUN apt-get -q update \
    && apt-get -q upgrade -y \
    && apt-get -q install -y --no-install-recommends build-essential libz-dev zlib1g-dev ca-certificates pandoc python3 python3-tabulate graphviz time gcc g++ make unzip wget parallel \
    && rm -rf /var/lib/apt/lists/*

# GraalVM

## Download, verify, unpack, and install

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.0.0.2/graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && echo 'bd3fbe796e803c4fe5cd089371588d3d716fa3cdb653fe8dd6dba31b57bef934 graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && mv graalvm-ce-java11-21.0.0.2 /opt/

ENV PATH=/opt/graalvm-ce-java11-21.0.0.2/bin/:$PATH
ENV JAVA_HOME=/opt/graalvm-ce-java11-21.0.0.2/

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.0.0.2/native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar \
    && echo '24e5a08e2714aee343b22c266285090721ff882ab0a31b7e8e4a68585c38f421 native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar' | sha256sum --check \
    && gu -L install native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar \
    && rm native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar

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

ENV LD_LIBRARY_PATH=/usr/local/lib/

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

RUN java -version \
    && native-image --version \
    && ltlcross --version
