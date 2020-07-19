FROM ubuntu:latest

MAINTAINER Salomon Sickert

# Remember: Most commands are chained in order to reduce the number of created
# layers and hence the size of the final image.

# Default to UTF-8 file encoding

ENV LANG C.UTF-8

# Install base system

RUN apt-get -q update \
    && apt-get -q upgrade -y \
    && apt-get -q install -y --no-install-recommends build-essential zlib1g-dev ca-certificates pandoc python3 python3-tabulate graphviz time gcc g++ make netcat unzip wget \
    && rm -rf /var/lib/apt/lists/*

# GraalVM

## Download, verify, unpack, and install

RUN wget -q https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-20.1.0/graalvm-ce-java11-linux-amd64-20.1.0.tar.gz \
    && echo '18f2dc19652c66ccd5bd54198e282db645ea894d67357bf5e4121a9392fc9394 graalvm-ce-java11-linux-amd64-20.1.0.tar.gz' | sha256sum --check \
    && tar -zxvf graalvm-ce-java11-linux-amd64-20.1.0.tar.gz \
    && rm graalvm-ce-java11-linux-amd64-20.1.0.tar.gz \
    && mv graalvm-ce-java11-20.1.0 /opt/

ENV PATH=/opt/graalvm-ce-java11-20.1.0/bin/:$PATH
ENV JAVA_HOME=/opt/graalvm-ce-java11-20.1.0/

RUN gu install native-image

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
