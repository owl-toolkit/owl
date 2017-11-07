// Based on http://www.linuxhowtos.org socket tutorial
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>

#define BUFFER_SIZE 1024
#define END_STRING "--END--"

void error(const char *msg)
{
    perror(msg);
    exit(1);
}

int read_and_print(int sockfd, char *buffer, int len) {
    int n = read(sockfd, buffer, len);
    if (n < 0)
        error("Read");
    if (n == 0)
        return 0;
    printf("%s", buffer);
    return 1;
}

int check_and_rotate(char *read_buffer, char *buffer, int offset) {
    if (strstr(buffer, END_STRING) != NULL)
        return 0;

    // Copy last <offset> bytes to the beginning
    strncpy(buffer, read_buffer + BUFFER_SIZE - offset - 1, offset);
    // Zero everything in the read buffer
    bzero(read_buffer, BUFFER_SIZE);
    return 1;
}

int main(int argc, char *argv[])
{
    int sockfd, portno;
    struct sockaddr_in serv_addr;
    struct hostent *server;

    if (argc != 4) {
       fprintf(stderr, "usage %s hostname port formula\n", argv[0]);
       exit(0);
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

    // Write the spec
    if (write(sockfd, argv[3], strlen(argv[3])) < 0)
         error("Write");
    if (write(sockfd, "\n", strlen("\n")) < 0)
         error("Write");

    // Create cyclic read buffers. To be able to always detect the termination string, even if it is
    // chopped by buffer boundary, we copy the last few bits at the beginning of the buffer
    int offset = strlen(END_STRING);
    char buffer[BUFFER_SIZE + offset];
    char *read_buffer = buffer + offset;
    bzero(buffer, BUFFER_SIZE + offset);

    // Read the full buffer at the beginning
    if (read_and_print(sockfd, buffer, BUFFER_SIZE + offset - 1)
        && check_and_rotate(read_buffer, buffer, offset))
        // Now we only read into the read_buffer
        while (read_and_print(sockfd, read_buffer, BUFFER_SIZE - 1)
            && check_and_rotate(read_buffer, buffer, offset))
            ;

    close(sockfd);
    return 0;
}
