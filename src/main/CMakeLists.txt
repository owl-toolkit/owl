cmake_minimum_required(VERSION 3.10.3)

project(Owl)

# Setup C++

set(CMAKE_CXX_STANDARD 17)

# Add Library

add_subdirectory(cpp/library)

# Add Executable

add_subdirectory(cpp/example)
