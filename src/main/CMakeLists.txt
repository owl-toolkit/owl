cmake_minimum_required(VERSION 3.10.3)

project(Owl)

# Setup C++

set(CMAKE_CXX_STANDARD 17)

# Check JNI

find_package(Java REQUIRED)
find_package(JNI REQUIRED)

message("JNI Headers:")
message(${JNI_INCLUDE_DIRS})
message("JNI Libraries:")
message(${JNI_LIBRARIES})

# Add Library

add_subdirectory(cpp/library)

# Add Executable

include_directories(${JNI_INCLUDE_DIRS} "headers")
add_subdirectory(cpp/example)
