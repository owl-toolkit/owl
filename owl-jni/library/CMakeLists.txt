cmake_minimum_required(VERSION 3.8)

# Define Library

add_library(owl
        include/owl.h
        include/owl-automaton.h
        include/owl-formula.h
        src/owl-private.h
        src/owl.cc
        src/owl-automaton.cc
        src/owl-formula.cc)
include_directories(owl ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_LIST_DIR}/include)
target_link_libraries(owl ${JNI_LIBRARIES})