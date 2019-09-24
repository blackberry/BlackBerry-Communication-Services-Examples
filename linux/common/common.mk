#*****************************************************************************
# Copyright (c) 2019 BlackBerry Limited.  All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#------------------------------------------------------------------------------
#
# This is a Makefile that will build one sample app against the BlackBerry
# Secure Spark SDK for Linux.
#
# Just include this from an app Makefile after setting the $(APP) variable.
#
# This builds for a single target architecture (and debug setting) per
# invocation.
#
# In addition to the SDK headers and libraries, some other packages (and their
# dependencies) are required.  These other packages are available as
# installable packages on Debian, Ubuntu, and Raspbian as:
#
#   libjsoncpp-dev
#
# To build this program, change to the directory containing this Makefile and
# type "make".  A debug version of the program will be compiled and linked.
#
#------------------------------------------------------------------------------
# Important Targets
#
#  all
#
#    The default target.  This builds the sample program for a single target
#    architecture and $(DEBUG) setting.
#
#  clean
#
#    Delete all intermediate and final outputs for all target architectures
#    and $(DEBUG) combinations.
#
#------------------------------------------------------------------------------
# Important Variables
#
#
#  APP
#
#    The name of the app to build.
#
#  V
#
#    When not empty, show the exact commands executed by the Makefile.
#
#  DEBUG
#
#    When it has any non-empty value, build a debug artefact.  Default is from
#    the environment.
#
#  SPARK_SDK
#
#    The directory containing the SDK artefacts.  This must be set to the sdk/
#    directory that came in your SDK package (the one that contains the
#    include/ and lib/ directories).
#
#    To make it easier to compile these example programs, this defaults to
#    "../../sdk/" if and only if that directory exists.
#
#    For ease of use as a demonstration program, this makefile will link the
#    resulting binary with an embedded absolute runtime dynamic link search
#    path (aka "RPATH") to the $(SPARK_SDK) lib directory for x86_64 and "."
#    for rpi.  When running the demonstration program on an rpi, it's handy to
#    be able to copy the binary and its SDK shared libraries into the same
#    directory.  For a shippable program, you'd probably want to place the SDK
#    shared libraries somewhere else and use a different RPATH or some other
#    mechanism to locate them.
#
#  CXX
#
#    The C++ compiler and linker to use.  Must be at least as capable as
#    gcc-6.3.  Default is "g++".  The target architecture will be guessed from
#    this compiler's output.
#
#    This option is useful in combination with $(SYSROOT) to perform a cross
#    compilation.
#
#  SYSROOT
#
#    Iff set, then this will be added to the system include and library search
#    paths to ease cross-compilation.  This is expected to be the directory
#    under which additional sysroot usr/include/, usr/lib/, and lib/
#    directories can be found (beyond those embedded in the cross toolchain).
#
#  SYSROOT_ARCH
#
#    Set this to override the path element within the $(SYSROOT) that the
#    target architecture's libraries are stored within.  The default for
#    Raspberry Pi targets is the Raspbian (and Debian and Ubuntu) name, which
#    can be different than the cross compiler's built-in triplet name (for
#    crosstool-ng cross toolchains, for example): "arm-linux-gnueabihf"
# 
#------------------------------------------------------------------------------
# Configuration

# Did they specify a $(SPARK_SDK) location?
ifeq ($(SPARK_SDK),)
  # Is there a "../../sdk/" directory?  If so, use that as a default.
  SPARK_SDK := $(wildcard ../../sdk/)
  ifeq ($(SPARK_SDK),)
    $(error "Specify the BlackBerry Secure Spark SDK location with: make SPARK_SDK=<dir>")
  endif
endif

#------------------------------------------------------------------------------
# Project Definition

# The name of the app.
ifeq ($(APP),)
  $(error "Specify the APP variable with the app name before including $(lastword $(MAKEFILE_LIST))")
endif

# Location of the common sources shared between applications.
COMMON_DIR := ../common/src

# Source files to build.
SRCS := $(wildcard src/app/*.cc $(COMMON_DIR)/common/*.cc)

#------------------------------------------------------------------------------
# Computed Values

# Hide commands?
hide := $(if $(V),,@)

# Summarize commands (as a recipe command line).  Arguments are verb, noun,
# and optional architecture.
ifneq ($(V),)
  summary :=
else
  summary = -@printf '%s %-9s %s\n' '[$(if $3,$3,$(ARCH)$(if $(DEBUG),.g))]' '$1' '$2'
endif

# Toolchain configuration.
CXX ?= g++

# The target architecture.
TRIPLET := $(shell $(CXX) -dumpmachine)
ifeq ($(TRIPLET),)
  $(error "Can't determine triplet from CXX=$(CXX)")
endif

# Does the compiler seem like it's going to work?
ifneq ($(filter x86_64-%,$(TRIPLET)),)
  ARCH := x86_64
endif
ifneq ($(filter armv8-% arm-%,$(TRIPLET)),)
  ARCH := rpi
  # The default sysroot path name for this target's binaries.
  SYSROOT_ARCH ?= arm-linux-gnueabihf
endif
ifeq ($(ARCH),)
  $(error "Can't determine SDK ARCH value from CXX=$(CXX) triplet=$(TRIPLET)")
endif

# The directory for the current architecture.
ARCH_DIR := $(ARCH)$(if $(DEBUG),.g)

# Output directories.
DEP_DIR := dep/$(ARCH_DIR)
OBJ_DIR := obj/$(ARCH_DIR)
APP_DIR := app/$(ARCH_DIR)

# Compute the directories containing all the source files.
SRCS_DIRS := $(sort $(dir $(SRCS)))

# Compute the object filenames.  This won't handle two .cc files with the same
# name in two different source directories, but it's good enough for us.
OBJS := $(patsubst %.cc,$(OBJ_DIR)/%.o,$(notdir $(SRCS)))

# System libraries to link with.
LIBS := \
  jsoncpp

# Include paths.
INCLUDE_DIRS := \
  $(COMMON_DIR) \
  $(SPARK_SDK)/include/

#------------------------------------------------------------------------------
# Build Rules

# Set the shell.
SHELL := /bin/bash

# By default, we build the app.
all: $(APP)

# Set default goal.
.DEFAULT_GOAL := all
.PHONY: all clean

# Delete on error.
.DELETE_ON_ERROR:

# Disable Unused Built-in Rules
.SUFFIXES:
%:: %,v
%:: RCS/%,v
%:: RCS/%
%:: s.%
%:: SCCS/s.%

# C++ compiler flags.
CXXFLAGS := \
  -std=c++14 \
  -fPIC \
  -fno-strict-aliasing \
  -fstack-protector-strong \
  -fdiagnostics-color=auto

# C++ strict warnings.
CXXFLAGS += \
  -Wall \
  -Werror \
  -Wno-deprecated-declarations \
  -Wclobbered \
  -Wempty-body \
  -Wignored-qualifiers \
  -Wtype-limits \
  -Wuninitialized \
  -Wshift-negative-value \
  -Wunused-but-set-parameter \
  -Wduplicated-cond \
  -Wlogical-op \
  -Wno-maybe-uninitialized \
  -Wformat \
  -Wformat-security

# Include directories.
CXXFLAGS += $(addprefix -I,$(INCLUDE_DIRS))

# Flags that ask the compiler to emit dependency information while it
# compiles.  Expanded only in the object compilation rule.
DEPFLAGS = -MMD -MT $@ -MP -MF $(DEP_DIR)/$*.d

# C++ link flags.
LDFLAGS := \
  -fPIE \
  -pie \
  -Wl,-znow

# What kind of build?
ifeq ($(DEBUG),)
  # Release.  Produce an optimized, assertion-free, stripped binary.
  CXXFLAGS += \
    -O2 \
    -DNDEBUG \
    -s
else
  # Debug.  Produce an unoptimized, asserting, debuggable binary.

  # Flags common to release and debug.
  DEBUG_FLAGS := \
    -ggdb \
    -fno-var-tracking \
    -fno-var-tracking-assignments

  # Debug.
  CXXFLAGS += $(DEBUG_FLAGS)
  LDFLAGS  += $(DEBUG_FLAGS)
endif

# Set a convenient runtime dynamic library path right into the binary as an
# Elf RPATH.  On x86_64, use the absolute path to the SDK libraries, and on
# rpi, use "." for maximum convenience.
#
# Note that we usually _don't_ use $(DEBUG) here because the SDK artefacts
# only come as release artefacts.  We'll only try to use SDK debug artefacts
# if $(SPARK_SDK_DEBUG) is set, but using that variable requires a matching
# copy of the SDK.
#
LDFLAGS += -Wl,-rpath $(if \
  $(filter rpi,$(ARCH)),.,$(realpath $(SPARK_SDK)/lib/$(ARCH)$(if $(SPARK_SDK_DEBUG),.g)))

# Compute the SDK library path for link-time search and add it to the link-time
# shared library search path only (before any other -rpath-link flags).
SDK_LIB_PATH := $(SPARK_SDK)/lib/$(ARCH)$(if $(SPARK_SDK_DEBUG),.g)
LDFLAGS += -L$(SDK_LIB_PATH) -Wl,-rpath-link,$(SDK_LIB_PATH)

# Enumerate the SDK libraries that we'll link with by full path, in reverse
# dependency order.
SDK_LIBS := \
  bbmcore \
  ffmpeg

# Compute the full path to those shared libraries, for dependencies.
SDK_LIBS_FILES := $(addsuffix .so,$(addprefix $(SDK_LIB_PATH)/lib,$(SDK_LIBS)))

# Iff there's a $(SYSROOT) set, configure the compiler and linker flags for
# it.
ifneq ($(SYSROOT),)
  # Add the additional sysroot include path.
  CXXFLAGS += -isystem $(SYSROOT)/usr/include/

  # Add the additional sysroot link paths.
  LDFLAGS += \
    -L$(SYSROOT)/usr/lib/$(SYSROOT_ARCH)/ \
    -Wl,-rpath-link,$(SYSROOT)/lib/$(SYSROOT_ARCH)/ \
    -Wl,-rpath-link,$(SYSROOT)/usr/lib/$(SYSROOT_ARCH)/
endif

# Finish the link library list with the system libraries.
SYS_LIBS := $(addprefix -l,$(LIBS))

# How to make the output directories.
define make-dir
$1:
	$$(call summary,mkdir,$$@)
	$$(hide) mkdir -p $$@

endef
$(foreach dir,$(DEP_DIR) $(OBJ_DIR) $(APP_DIR),$(eval $(call make-dir,$(dir))))

# Tell make that we're creating the dependency files in another rule.
$(DEP_DIR)/%.d:
	@:

# Don't automatically delete the dependency files, and include them.
.PRECIOUS: $(DEP_DIR)/%.d
include $(wildcard $(DEP_DIR)/*.d)

# How to compile an object (and its dependency file).  Touch the .o first to
# ensure that it has changed so that .DELETE_ON_ERROR will remove it on every
# compilation failure.
define object-rule
$(OBJ_DIR)/%.o: $1/%.cc $(DEP_DIR)/%.d | $(OBJ_DIR) $(DEP_DIR)
	$$(call summary,compile,$$(notdir $$@))
	$(hide) touch $$@ && $(CXX) $(CXXFLAGS) $(value DEPFLAGS) -c -o $$@ $$<

endef
$(foreach dir,$(SRCS_DIRS),$(eval $(call object-rule,$(dir))))

# How to link the binary.  Note that we include all the SDK shared libraries as
# DT_NEEDED dependencies with --no-as-needed so that they will be found at load
# time, just to keep things simpler w.r.t. our use of RPATH.
$(APP_DIR)/$(APP): $(OBJS) $(SDK_LIBS_FILES) | $(APP_DIR)
	$(call summary,link,$@)
	$(hide) $(CXX) $(LDFLAGS) $(OBJS) \
	  -Wl,--push-state,--no-as-needed $(addprefix -l,$(SDK_LIBS)) -Wl,--pop-state \
	  $(SYS_LIBS) -o $@

# An alias for the binary rule.
.PHONY: $(APP)
$(APP): $(APP_DIR)/$(APP)

# How to clean everything.
clean:
	$(call summary,clean,,all)
	$(hide) rm -rf dep/ obj/ app/

#*****************************************************************************
