cmake_minimum_required(VERSION 3.22)
project(whisper)

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whispercpp)

add_compile_definitions(WHISPER_VERSION="unknown")

add_library(
        whisper
        SHARED
        jni.c
        ${WHISPER_DIR}/src/whisper.cpp
)

add_subdirectory(${WHISPER_DIR}/ggml ggml-build)

target_include_directories(
        whisper PRIVATE
        ${WHISPER_DIR}/include
)

target_compile_definitions(whisper PUBLIC GGML_USE_CPU)

find_library(log-lib log)
target_link_libraries(whisper ${log-lib} android ggml)
