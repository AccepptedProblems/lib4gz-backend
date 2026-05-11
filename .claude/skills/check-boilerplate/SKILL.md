---
name: check-boilerplate
description: Scan the codebase for duplicated boilerplate code and suggest ways to reduce it. Use when the user wants to find repeated patterns, reduce code duplication, or improve code reuse.
argument-hint: "[file-or-directory]"
allowed-tools: Read, Grep, Glob, Bash(wc:*), Bash(diff:*)
---

# Check Boilerplate Code

Scan `$ARGUMENTS` (or the entire `src/` directory if no argument is given) for duplicated boilerplate patterns and suggest concrete refactoring opportunities.

## What to check

### 1. Service Layer Boilerplate
Look for repeated patterns across service implementations:
- **Reactive wrapping**: `Mono.fromCallable { ... }.subscribeOn(Schedulers.boundedElastic())` repeated in every method. Suggest extracting a helper like `fun <T> blocking(block: () -> T): Mono<T>`.
- **Entity fetching + not-found**: `.orElseThrow { ResourceNotFoundException("...") }` repeated per entity type. Suggest a generic `findOrThrow(repo, id, name)` extension.
- **Authorization checks**: Repeated `enrollmentService.isTeacherInCourse()` / `isEnrolledInCourse()` guard blocks. Suggest an annotation-based or AOP approach.
- **CRUD method structure**: If multiple services follow the exact same create/read/update/delete skeleton, flag it and suggest a base service or generic CRUD service.

### 2. Controller Layer Boilerplate
- **Header extraction**: `@RequestHeader(PZRequestHeader.USER_ID) userId: String` repeated on every endpoint. Suggest a `@CurrentUserId` annotation or resolver.
- **Identical endpoint shapes**: If multiple controllers have the same GET/POST/PUT/DELETE structure with only the service call differing, flag it.
- **Response wrapping**: Repeated `Mono<ResponseEntity<T>>` patterns that could use a shared response builder.

### 3. Mapper Boilerplate
- **Manual field mapping**: If mappers are copying fields one-by-one from entity to DTO, suggest using Kotlin data class `copy()`, MapStruct, or a shared mapping utility.
- **Companion object pattern**: Multiple mappers with identical structure in the same file. Check if they can be simplified.

### 4. Entity Boilerplate
- **Soft delete annotations**: `@SQLDelete` and `@Where(clause = "deleted_at IS NULL")` repeated on every entity. Suggest a `@SoftDeletable` base class or interface.
- **Audit fields**: If `createdAt`, `updatedAt`, `deletedAt` appear in multiple entities, suggest a `@MappedSuperclass` base entity.
- **ID generation**: Repeated `IdGenerator.generate("prefix")` in `@PrePersist`. Suggest a base entity or annotation.

### 5. General Duplication
- **Copy-pasted blocks**: Look for 5+ lines of code that appear nearly identical in multiple files.
- **Similar exception handling**: Repeated try-catch or error mapping logic.
- **Configuration patterns**: Repeated bean definitions or security configurations.

## Output format

For each finding, report:

1. **Pattern name** - What the boilerplate is
2. **Occurrences** - List the files and line numbers where it appears
3. **Impact** - How many lines of code are duplicated
4. **Suggestion** - A concrete, minimal refactoring with a short code example
5. **Priority** - High (10+ occurrences), Medium (5-9), Low (2-4)

Sort findings by priority (highest first).

## Rules

- Only flag genuine duplication (3+ occurrences minimum). Two similar blocks are not boilerplate.
- Suggestions must be idiomatic Kotlin + Spring Boot. Do not suggest patterns that fight the framework.
- Keep suggestions minimal - don't over-engineer. A simple extension function beats a complex abstraction.
- Do NOT suggest changes to code that is intentionally explicit for readability.
- Show before/after code snippets for each suggestion.
