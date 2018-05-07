# Define Executable

add_executable(owl-example example.cc)

if (CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # Link JDK 10 first to work around macOS Java Fuckup.
    set(DARWIN_FIX "/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/lib/jli/libjli.dylib")
    target_link_libraries(owl-example ${DARWIN_FIX} ${JNI_LIBRARIES} owl)
else()
    target_link_libraries(owl-example ${JNI_LIBRARIES} owl)
endif()
