
cmake_minimum_required(VERSION 3.4.1)

set(lib_DIR ${CMAKE_SOURCE_DIR}/../../../libs)
include_directories(${CMAKE_SOURCE_DIR}/../../../include)

#link_directories(${CMAKE_SOURCE_DIR}/../../../include)

#add_library(MNN SHARED IMPORTED)

#set_target_properties(
#        MNN
#        PROPERTIES IMPORTED_LOCATION
#        ${lib_DIR}/arm64-v8a/libMNN.so
#)


add_library(
             native-lib

             SHARED

             native-lib.cpp )


find_library( log-lib log android )
find_library(jnigraphics-lib jnigraphics)

#set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fopenmp")
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fopenmp")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=gnu99 -fvisibility=hidden -fomit-frame-pointer -fstrict-aliasing -ffunction-sections -fdata-sections -ffast-math -flax-vector-conversions")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fvisibility=hidden -fvisibility-inlines-hidden -fomit-frame-pointer -fstrict-aliasing -ffunction-sections -fdata-sections -ffast-math -fno-rtti -fno-exceptions -flax-vector-conversions")
set(CMAKE_LINKER_FLAGS "${CMAKE_LINKER_FLAGS} -Wl,--gc-sections")


add_library(MNN SHARED IMPORTED)
add_library(MNNCL SHARED IMPORTED)
add_library(MNNEXPRESS SHARED IMPORTED)
add_library(MNNVULKAN SHARED IMPORTED)


set_target_properties(MNN PROPERTIES IMPORTED_LOCATION ${lib_DIR}/${ANDROID_ABI}/libMNN.so)
set_target_properties(MNNCL PROPERTIES IMPORTED_LOCATION ${lib_DIR}/${ANDROID_ABI}/libMNN_CL.so)
set_target_properties(MNNEXPRESS PROPERTIES IMPORTED_LOCATION ${lib_DIR}/${ANDROID_ABI}/libMNN_Express.so)
set_target_properties(MNNVULKAN PROPERTIES IMPORTED_LOCATION ${lib_DIR}/${ANDROID_ABI}/libMNN_Vulkan.so)
#add_library(mnncore SHARED native-lib.cpp)

target_link_libraries( # Specifies the target library
                        #mnn
                       native-lib
                        MNN
                        MNNCL
                        MNNEXPRESS
                        MNNVULKAN
                        -ljnigraphics
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
        )
