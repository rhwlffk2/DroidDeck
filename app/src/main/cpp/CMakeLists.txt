cmake_minimum_required(VERSION 3.22.1)
project(droiddeck C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_subdirectory(adrenotools)

# DXVK's DXBC->SPIR-V translator is zlib licensed (vendored under thirdparty/dxbc).
add_subdirectory(thirdparty/dxbc)

set(DDFG_UPSTREAM "v0.3.0")
set(DDFG_CHAIN_SRC
        framegen/ddfg/framegen.cpp
        framegen/ddfg/framegen.hpp
        framegen/ddfg/record_impl.inc
        framegen/ddfg/config.hpp
        framegen/ddfg/embedded_shaders.hpp
        framegen/ddfg/ddfg_engine.cpp
)
set(DDFG_HASH_INPUT "")
foreach(f ${DDFG_CHAIN_SRC})
    file(SHA256 "${CMAKE_CURRENT_SOURCE_DIR}/${f}" _h)
    string(APPEND DDFG_HASH_INPUT "${_h}")
endforeach()
string(SHA256 DDFG_CHAIN_HASH_FULL "${DDFG_HASH_INPUT}")
string(SUBSTRING "${DDFG_CHAIN_HASH_FULL}" 0 12 DDFG_CHAIN_HASH)
message(STATUS "dd-fg chain: win-fg ${DDFG_UPSTREAM} (src ${DDFG_CHAIN_HASH})")

add_subdirectory(waylandcomp)
add_library(sdnative SHARED sdnative/sdnative.c)
target_link_libraries(sdnative log)

add_library(deviceinfo SHARED deviceinfo/vkinfo.c)
target_link_libraries(deviceinfo dl)

add_library(termux SHARED termux/termux.c)

