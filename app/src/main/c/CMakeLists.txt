cmake_minimum_required(VERSION 3.22.1)

project(embedded_python C)

add_library(embedded_python SHARED embedded_python.c)

find_library(log-lib log)

target_link_libraries(embedded_python ${log-lib})
