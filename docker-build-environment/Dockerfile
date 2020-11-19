FROM ubuntu:latest

MAINTAINER Salomon Sickert

# Remember: Most commands are chained in order to reduce the number of created
# layers and hence the size of the final image.

# Default to UTF-8 file encoding

ENV LANG C.UTF-8

# Install base system

RUN apt-get -q update \
    && apt-get -q upgrade -y \
    && apt-get -q install -y --no-install-recommends build-essential libz-dev zlib1g-dev ca-certificates pandoc python3 python3-tabulate graphviz time gcc g++ make unzip wget \
    && rm -rf /var/lib/apt/lists/*

# GraalVM

## Download, verify, unpack, and install

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-20.3.0/graalvm-ce-java11-linux-amd64-20.3.0.tar.gz \
    && echo '557aafd6f4fb8c103ce853adda452a7dc3eeca2ef1af00940b70efafe00fb3d5 graalvm-ce-java11-linux-amd64-20.3.0.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-20.3.0.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-20.3.0.tar.gz \
    && mv graalvm-ce-java11-20.3.0 /opt/

ENV PATH=/opt/graalvm-ce-java11-20.3.0/bin/:$PATH
ENV JAVA_HOME=/opt/graalvm-ce-java11-20.3.0/

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-20.3.0/native-image-installable-svm-java11-linux-amd64-20.3.0.jar \
    && echo '6e67f3c5cc329956fe9bc33d571c2e771eff8f4f541bca275bb62744026d3726 native-image-installable-svm-java11-linux-amd64-20.3.0.jar' | sha256sum --check \
    && gu -L install native-image-installable-svm-java11-linux-amd64-20.3.0.jar

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

# Print Configuration

RUN java -version \
    && native-image --version \
    && ltlcross --version
