cmake_minimum_required(VERSION 3.12)
if(COMMAND cmake_policy)
  cmake_policy(SET CMP0003 NEW)
endif(COMMAND cmake_policy)

# Define Executable

find_package(JNI REQUIRED)

# Check JNI

message("JNI Headers:")
message(${JNI_INCLUDE_DIRS})
message("JNI Libraries:")
message(${JNI_LIBRARIES})

include_directories(${JNI_INCLUDE_DIRS} "../../headers")

add_executable(owl-example example.cc)

if (CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # Link JDK 10 first to work around macOS Java Fuckup.
    set(DARWIN_FIX "/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/lib/jli/libjli.dylib")
    target_link_libraries(owl-example ${DARWIN_FIX} ${JNI_LIBRARIES} owl)
else()
    target_link_libraries(owl-example ${JNI_LIBRARIES} owl)
endif()
