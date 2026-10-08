# GRADLE-GATE-JRE-TOOLCHAIN (2026-10-09) — lessons/machinery

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- Confined gate env forwards only PATH/LANG/LC_ALL/TERM and rebinds HOME to scratch, so JAVA_HOME and ~/.gradle are invisible to Gradle. Without a java toolchain block in the build, the daemon runs on whatever JVM resolves java — on this host temurin-17.jre (no javac), failing every Gradle gate with "does not provide required capabilities: [JAVA_COMPILER]". Fix is a committed org.gradle.java.home=/opt/homebrew/opt/openjdk@17 in repo gradle.properties (protected path — needs a boss grant), not installations.paths (additive, and only feeds toolchain resolution which a toolchain-free build never uses).
