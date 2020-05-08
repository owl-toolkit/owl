// Based on http://www.linuxhowtos.org socket tutorial
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <errno.h>

#define BUFFER_SIZE 8192

void error(const char *msg)
{
    perror(msg);
    exit(1);
}

int main(int argc, char *argv[])
{
    int sockfd, portno;
    struct sockaddr_in serv_addr;
    struct hostent *server;
    char buffer[BUFFER_SIZE];

    if (argc == 2 && !strcmp(argv[1], "--version")) {
        fprintf(stdout, "owl-client version 1.0");
        exit(0);
    }
    if (argc < 3 || argc > 4) {
       fprintf(stderr, "usage %s hostname port formula?\n", argv[0]);
       exit(1);
    }

    // Open socket
    portno = atoi(argv[2]);
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0)
        error("Open");

    // Parse server address
    server = gethostbyname(argv[1]);
    if (server == NULL) {
        fprintf(stderr, "No such host\n");
        exit(1);
    }

    // Initialize connection struct
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    bcopy((char *) server->h_addr, (char *) &serv_addr.sin_addr.s_addr, server->h_length);
    serv_addr.sin_port = htons(portno);

    // Connect to the server
    if (connect(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0)
        error("Connect");

    if (argc == 4) {
        // Send request
        if (send(sockfd, argv[3], strlen(argv[3]), 0) < 0 || send(sockfd, "\n", strlen("\n"), 0) < 0)
            error("Write");
    } else {
        while (fgets(buffer, BUFFER_SIZE, stdin)) {
            if (send(sockfd, buffer, strlen(buffer), 0) < 0)
                error("Write");
        }
    }

    // Close send channel
    if (shutdown(sockfd, SHUT_WR) < 0)
        error("Close Send");

    // Recieve response
    int recv_bytes = 0;

    do {
        recv_bytes = recv(sockfd, buffer, sizeof(char) * BUFFER_SIZE, MSG_WAITALL);

        if (recv_bytes < 0)
            error("Read");

        fwrite(buffer, sizeof(char), recv_bytes, stdout);
    } while (recv_bytes > 0);

    // No need to shutdown the recieve channel, since the server shutdowns that.
    close(sockfd);
    return 0;
}
