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

# Define Library

add_library(owl
        headers/owl.h
        headers/owl-automaton.h
        headers/owl-formula.h
        cpp/library/owl-private.h
        cpp/library/owl.cc
        cpp/library/owl-automaton.cc
        cpp/library/owl-formula.cc)
include_directories(owl ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_LIST_DIR}/include)
target_link_libraries(owl ${JNI_LIBRARIES})

# Define Executable

add_executable(owl-example cpp/example/example.cc)
include_directories(${JNI_INCLUDE_DIRS} "headers")

if (CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # Link JDK 9 first to work around macOS Java Fuckup.
    set(DARWIN_FIX "/Library/Java/JavaVirtualMachines/jdk-9.jdk/Contents/Home/lib/jli/libjli.dylib")
    target_link_libraries(owl-example ${DARWIN_FIX} ${JNI_LIBRARIES} owl)
else()
    target_link_libraries(owl-example ${JNI_LIBRARIES} owl)
endif()