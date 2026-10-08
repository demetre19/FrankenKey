# GATE-ENV-JRE-NO-COMPILER (2026-10-08) — lessons/machinery

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- Gate executor env forwards only PATH/LANG/LC_ALL/TERM and rebinds HOME to scratch; Gradle toolchain then resolves the JRE on PATH as 'Current JVM' (no JAVA_COMPILER). Fix = prefix gate argv '/usr/bin/env JAVA_HOME=<jdk>' like g3-lint-vital — JAVA_HOME alone is ignored by toolchain resolution; org.gradle.java.home or installations.paths+auto-detect=false are the alternates. A receipt with failures=[] fails closed — waivers cannot bind a pre-test toolchain death.
