# Define Library

find_package(JNI REQUIRED)

include_directories(${JNI_INCLUDE_DIRS} "../../headers")

add_library(owl owl.cc owl-base.cc owl-automaton.cc owl-formula.cc)

target_link_libraries(owl ${JNI_LIBRARIES})
