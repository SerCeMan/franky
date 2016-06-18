cmake_minimum_required(VERSION 3.5)
project(franky)

set(JAVA_HOME /usr/lib/jvm/java-8-oracle)
set(LIB_NAME libasyncProfiler.so)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

include_directories(
        ${JAVA_HOME}/include
        ${JAVA_HOME}/include/linux
        ${CMAKE_CURRENT_BINARY_DIR})

set(SOURCE_FILES
        src/profiler.cpp
        src/profiler.h
        src/vmEntry.cpp
        src/vmEntry.h)

ADD_SUBDIRECTORY(proto)


set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../lib)

add_library(frankyagent SHARED ${SOURCE_FILES})
TARGET_LINK_LIBRARIES(frankyagent proto ${PROTOBUF_LIBRARY})