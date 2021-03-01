FROM ubuntu:20.10

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

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.0.0.2/graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && echo 'bd3fbe796e803c4fe5cd089371588d3d716fa3cdb653fe8dd6dba31b57bef934 graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-21.0.0.2.tar.gz \
    && mv graalvm-ce-java11-21.0.0.2 /opt/

ENV PATH=/opt/graalvm-ce-java11-21.0.0.2/bin/:$PATH
ENV JAVA_HOME=/opt/graalvm-ce-java11-21.0.0.2/

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.0.0.2/native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar \
    && echo '24e5a08e2714aee343b22c266285090721ff882ab0a31b7e8e4a68585c38f421 native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar' | sha256sum --check \
    && gu -L install native-image-installable-svm-java11-linux-amd64-21.0.0.2.jar

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
