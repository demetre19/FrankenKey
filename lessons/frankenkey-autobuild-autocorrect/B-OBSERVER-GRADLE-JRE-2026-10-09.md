# B-OBSERVER-GRADLE-JRE (2026-10-09) — lessons/frankenkey-autobuild-autocorrect

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- prd_step gate env drops JAVA_HOME — Gradle launches on ~/Library JRE (no javac); pin via gradle.properties org.gradle.java.installations.paths+auto-detect=false or javaToolchains
