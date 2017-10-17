cmake_minimum_required(VERSION 3.8)

# Define Executable

add_executable(owl-example example.cc)
include_directories(${JNI_INCLUDE_DIRS} "../library/include")

if (CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # Link JDK 9 first to work around macOS Java Fuckup.
    set(DARWIN_FIX "/Library/Java/JavaVirtualMachines/jdk-9.jdk/Contents/Home/lib/jli/libjli.dylib")
    target_link_libraries(owl-example ${DARWIN_FIX} ${JNI_LIBRARIES} owl)
else()
    target_link_libraries(owl-example ${JNI_LIBRARIES} owl)
endif()