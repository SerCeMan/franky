cmake_minimum_required(VERSION 3.2)
project(franky)


if (APPLE)
    set(JAVA_HOME /Library/Java/JavaVirtualMachines/jdk1.8.0_102.jdk/Contents/Home)
    set(PLATFORM_INCLUDE ${JAVA_HOME}/include/darwin)
    set(LIB_NAME libasyncProfiler.dylib)
elseif (UNIX)
    set(JAVA_HOME $ENV{JAVA_HOME})
    set(PLATFORM_INCLUDE ${JAVA_HOME}/include/linux)
    set(LIB_NAME libasyncProfiler.so)
endif ()

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${JAVA_HOME}/include
        ${PLATFORM_INCLUDE}
        ${CMAKE_CURRENT_BINARY_DIR})

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fPIC")


set(SOURCE_FILES
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        src/profiler.cpp
        src/profiler.h
        src/logging.h
        src/vmEntry.cpp
        src/vmEntry.h)

ADD_SUBDIRECTORY(proto)


set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../lib)

add_library(frankyagent SHARED ${SOURCE_FILES})
TARGET_LINK_LIBRARIES(frankyagent proto ${PROTOBUF_LIBRARY})
