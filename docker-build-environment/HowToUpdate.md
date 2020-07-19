# How to update the docker build environment.

0. Update Dockerfile
1. Execute `git log` and get the 8 first characters of the hash sum.
2. `sudo docker pull ubuntu:latest`
3. `sudo docker build -t gitlab.lrz.de:5005/i7/owl:[hash sum] .`
4. `sudo docker push gitlab.lrz.de:5005/i7/owl:[hash sum]`
5. Update `.gitlab-ci.yml`
