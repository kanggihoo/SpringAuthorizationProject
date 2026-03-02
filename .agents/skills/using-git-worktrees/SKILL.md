---
name: using-git-worktrees
description: Use when starting feature work that needs isolation from current workspace or before executing implementation plans - creates isolated git worktrees with smart directory selection and safety verification
---

# Using Git Worktrees (Java/Spring Boot Optimized)

## Overview

Git worktrees create isolated workspaces sharing the same repository, allowing work on multiple branches simultaneously without switching.

**Core principle:** Always use the `.worktrees/` directory + verify safety = reliable isolation.

**Announce at start:** "I'm using the using-git-worktrees skill to set up an isolated workspace under .worktrees/."

## Directory Selection Process

We **ALWAYS** use the project-local `.worktrees/` directory. Do not look for other directories or global configurations.

```bash
LOCATION=".worktrees"
```

## Safety Verification

**MUST verify directory is ignored before creating worktree:**

```bash
# Check if directory is ignored (respects local, global, and system gitignore)
git check-ignore -q .worktrees 2>/dev/null
```

**If NOT ignored:**

Per Jesse's rule "Fix broken things immediately":

1. Add `.worktrees/` to `.gitignore`
2. Commit the change: `git commit -m "chore: ignore .worktrees directory"`
3. Proceed with worktree creation

**Why critical:** Prevents accidentally committing worktree contents to repository.

## Creation Steps

### 1. Create Worktree

```bash
# Determine full path
path=".worktrees/$BRANCH_NAME"

# Create worktree with new branch
git worktree add "$path" -b "$BRANCH_NAME"
cd "$path"
```

### 2. Run Project Setup (Java/Spring Boot)

Auto-detect and run appropriate setup using Gradle or Maven wrappers:

```bash
# Gradle
if [ -f gradlew ]; then
  ./gradlew build -x test
elif [ -f build.gradle ]; then
  gradle build -x test
fi

# Maven
if [ -f mvnw ]; then
  ./mvnw clean compile
elif [ -f pom.xml ]; then
  mvn clean compile
fi
```

### 3. Verify Clean Baseline

Run tests to ensure worktree starts clean before implementing new code:

```bash
# Gradle
if [ -f gradlew ]; then
  ./gradlew test
elif [ -f build.gradle ]; then
  gradle test
# Maven
elif [ -f mvnw ]; then
  ./mvnw test
elif [ -f pom.xml ]; then
  mvn test
fi
```

**If tests fail:** Report failures, ask whether to proceed or investigate.

**If tests pass:** Report ready.

### 4. Report Location

```
Worktree ready at <full-path>
Tests passing (<N> tests, 0 failures)
Ready to implement <feature-name>
```

## Quick Reference

| Situation                  | Action                                   |
| -------------------------- | ---------------------------------------- |
| Git worktree creation      | ALWAYS use `.worktrees/` directory.      |
| Directory not ignored      | Add `.worktrees/` to .gitignore + commit |
| Tests fail during baseline | Report failures + ask                    |

## Common Mistakes

### Skipping ignore verification

- **Problem:** Worktree contents get tracked, pollute git status
- **Fix:** Always use `git check-ignore` before creating worktree

### Proceeding with failing tests

- **Problem:** Can't distinguish new bugs from pre-existing issues
- **Fix:** Report failures, get explicit permission to proceed

## Example Workflow

```
You: I'm using the using-git-worktrees skill to set up an isolated workspace.

[Verify ignored - git check-ignore confirms .worktrees/ is ignored]
[Create worktree: git worktree add .worktrees/auth -b feature/auth]
[Run ./gradlew build -x test]
[Run ./gradlew test - 47 passing]

Worktree ready at /Users/kkh/Desktop/javaMiniProject/.worktrees/auth
Tests passing (47 tests, 0 failures)
Ready to implement auth feature
```

## Red Flags

**Never:**

- Create worktree without verifying it's ignored.
- Skip baseline test verification.
- Proceed with failing tests without asking.
- Use any directory other than `.worktrees/`.

**Always:**

- Force worktrees to `.worktrees/` directory.
- Verify directory is ignored.
- Auto-detect and run Java project setup (Gradle/Maven).
- Verify clean test baseline.

## Integration

**Called by:**

- **brainstorming** (Phase 4) - REQUIRED when design is approved and implementation follows
- **subagent-driven-development** - REQUIRED before executing any tasks
- **executing-plans** - REQUIRED before executing any tasks
- Any skill needing isolated workspace

**Pairs with:**

- **finishing-a-development-branch** - REQUIRED for cleanup after work complete
