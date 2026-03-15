# Ralph Progress Log

This file tracks progress across iterations. Agents update this file
after each iteration and it's included in prompts for context.

## Codebase Patterns (Study These First)

*Add reusable patterns discovered during development here.*
- For JPMS with Spring on the JavaFX desktop app, explicitly `requires spring.beans` and `requires spring.core` when `module-info.java` opens packages to those modules; relying on `spring.context` alone leaves compiler warnings.

---

## 2026-03-15 - US-001
- Verified and completed the Spring Boot desktop dependency setup using `spring-boot-starter` so DI, typed configuration support, and logging are available without introducing a web server.
- Added JaCoCo Maven plugin wiring so `.\mvnw.cmd clean verify jacoco:report` succeeds as required.
- Updated the Maven wrapper to store its distribution inside the project so the wrapper runs in this workspace without depending on an external home directory.
- Confirmed the JavaFX launcher still starts with the Spring dependencies present via a direct `Launcher` module-path smoke test.
- Files changed: `pom.xml`, `.mvn/wrapper/maven-wrapper.properties`, `src/main/java/module-info.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - JPMS + Spring needs explicit `requires` entries for Spring modules referenced in `opens ... to ...`.
    - Keeping Maven wrapper state project-local avoids environment-specific failures during automated verification.
  - Gotchas encountered
    - The required `jacoco:report` goal failed until `org.jacoco:jacoco-maven-plugin` was declared in `pom.xml`.
    - JavaFX startup in this sandbox logs cache-directory warnings under `C:\Users\CodexSandboxOnline\.openjfx`, but the launcher still starts and remains running.
---
