cmake_minimum_required(VERSION 3.18.1)
project(forklottie)

# Build rlottie statically into our JNI library. Disable the dlopen-based external
# image module (not needed for TGS) and tests to keep the build lean.
set(LOTTIE_MODULE OFF CACHE BOOL "" FORCE)
set(LOTTIE_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# rlottie source lives in third_party (cloned, same pattern as TDLib).
set(RLOTTIE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/rlottie)
add_subdirectory(${RLOTTIE_DIR} rlottie_build EXCLUDE_FROM_ALL)

add_library(forklottie SHARED rlottie_jni.cpp)
target_include_directories(forklottie PRIVATE ${RLOTTIE_DIR}/inc)
target_link_libraries(forklottie rlottie android jnigraphics log)
