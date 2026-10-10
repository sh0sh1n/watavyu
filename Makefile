.DEFAULT_GOAL := help

JAVA_HOME ?= $(shell asdf where java 2>/dev/null)
export JAVA_HOME

GRADLEW := ./gradlew

.PHONY: help build run test clean

help:
	@printf '%s\n' 'build  Compile the application' 'run    Launch the desktop application' 'test   Run the test suite' 'clean  Remove Gradle build outputs'

build:
	$(GRADLEW) classes

run:
	$(GRADLEW) run

test:
	$(GRADLEW) test

clean:
	$(GRADLEW) clean