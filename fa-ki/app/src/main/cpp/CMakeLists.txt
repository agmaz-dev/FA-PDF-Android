cmake_minimum_required(VERSION 3.22.1)
project("fa-ki" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Rechenwerke (Prozessor, Adreno-Grafik) als eigene Bibliotheken, die zur Laufzeit geladen werden.
# Fehlt OpenCL auf einem Gerät, läuft die KI trotzdem auf dem Prozessor.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_OPENCL ON CACHE BOOL "" FORCE)
set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# Snapdragon 8 Elite (Galaxy Z Fold 7): Punktprodukt- und Int8-Matrixbefehle nutzen
# Nur Befehle, die jedes neuere Handy kennt; KleidiAI nutzt schnellere Befehle selbst, wenn vorhanden
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} build-llama)

add_library(fa-ki SHARED fa_ki.cpp)
target_include_directories(fa-ki PRIVATE ${LLAMA_DIR}/common ${LLAMA_DIR}/include ${LLAMA_DIR}/ggml/include)
target_link_libraries(fa-ki llama llama-common android log)
