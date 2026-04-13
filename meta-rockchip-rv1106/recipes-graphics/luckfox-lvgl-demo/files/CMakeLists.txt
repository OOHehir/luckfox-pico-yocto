cmake_minimum_required(VERSION 3.13)
project(luckfox-lvgl-demo C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# lv_conf.h lives in the current source dir (we ship it inline with
# the recipe so the LVGL upstream source stays untouched).
add_compile_definitions(LV_CONF_INCLUDE_SIMPLE)
include_directories(BEFORE ${CMAKE_CURRENT_SOURCE_DIR})

# LVGL upstream lives in ./lvgl — CMakeLists.txt there defines the
# `lvgl` static library target.
add_subdirectory(lvgl)

add_executable(luckfox-lvgl-demo main.c)
target_link_libraries(luckfox-lvgl-demo PRIVATE lvgl lvgl::thorvg m)

install(TARGETS luckfox-lvgl-demo RUNTIME DESTINATION bin)
