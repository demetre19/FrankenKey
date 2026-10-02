.PHONY: test lint build

# FrankenKey is a PRD/spec + shipped-APK repo: the "toolchain" checks are
# document and manifest integrity, not compiled targets.

test:
	python3 scripts/check_docs.py test

lint:
	python3 scripts/check_docs.py lint

build:
	python3 scripts/check_docs.py build
