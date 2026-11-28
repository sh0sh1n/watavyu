.DEFAULT_GOAL := help

JAVA_HOME ?= $(shell asdf where java 2>/dev/null)
export JAVA_HOME

GRADLEW := ./gradlew

.PHONY: help build run test clean dist release

help:
	@printf '%s\n' 'build    Compile the application' 'run      Launch the desktop application' 'test     Run the test suite' 'clean    Remove Gradle build outputs' 'dist     Build and check the disk image (signed if SIGNING_IDENTITY is set)' 'release  Build, sign, notarize and staple the disk image'

build:
	$(GRADLEW) classes

run:
	$(GRADLEW) run

test:
	$(GRADLEW) test

clean:
	$(GRADLEW) clean

# SIGNING_IDENTITY names the Developer ID Application certificate, unless ~/.gradle/gradle.properties sets
# signingIdentity (BUILDING.md › Distribution).
SIGNING := $(if $(SIGNING_IDENTITY),"-PsigningIdentity=$(SIGNING_IDENTITY)")

dist:
	caffeinate -u -d $(GRADLEW) distDmg $(SIGNING)

release:
	caffeinate -u -d $(GRADLEW) distNotarize $(SIGNING)
