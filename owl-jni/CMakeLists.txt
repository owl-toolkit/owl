cmake_minimum_required(VERSION 3.8)

project(Owl)

# Setup C++

set(CMAKE_CXX_STANDARD 17)

# Check JNI

find_package(JNI REQUIRED)

message("JNI Headers:")
message(${JNI_INCLUDE_DIRS})
message("JNI Libraries:")
message(${JNI_LIBRARIES})

# Add sub-directories
add_subdirectory(library)
add_subdirectory(examples)