# Module Feature Documentation

## 1. Overview

Course modules are organizational units within a course, ordered by index. They serve as containers for lessons and provide hierarchical structure to course content. Each module belongs to exactly one course and maintains an ordered position via `orderIndex`.

---

## 2. Models

> Full model definitions: [models/module.yaml](../models/module.yaml)

### Entity
- **Module** — `modules` table, soft delete, unique constraint on (courseId, orderIndex)

### DTOs
- **CreateModuleRequest** — title (required), orderIndex?
- **UpdateModuleRequest** — all fields optional
- **ModuleResponse** — id, courseId, title, orderIndex, lessonCount?, timestamps

### Mapper
- **ModuleMapper** — `@Component` with `toResponse(module, lessonCount?)`

---

## 3. Repository

- **Annotation:** `@Repository`
- **Interface:** `ModuleRepo : JpaRepository<Module, String>`

```kotlin
@Repository
interface ModuleRepo : JpaRepository<Module, String> {

    fun findByCourse_IdOrderByOrderIndexAsc(courseId: String): List<Module>

    fun findByCourse_Id(courseId: String): List<Module>

    @Query("SELECT MAX(m.orderIndex) FROM Module m WHERE m.course.id = :courseId")
    fun findMaxOrderIndexByCourseId(courseId: String): Int?

    fun existsByCourse_IdAndOrderIndex(courseId: String, orderIndex: Int): Boolean

    fun countByCourse_Id(courseId: String): Long
}
```

## 4. Service

### Interface

```kotlin
interface ModuleService {
    fun createModule(courseId: String, userId: String, request: CreateModuleRequest): Mono<ModuleResponse>
    fun getModuleById(moduleId: String, userId: String): Mono<ModuleResponse>
    fun updateModule(moduleId: String, userId: String, request: UpdateModuleRequest): Mono<ModuleResponse>
    fun deleteModule(moduleId: String, userId: String): Mono<Void>
    fun listCourseModules(courseId: String, userId: String): Flux<ModuleResponse>
}
```

### Implementation

- **Dependencies:** `moduleRepo`, `courseRepo`, `enrollmentService`, `lessonRepo`

#### createModule(courseId, userId, request)
1. Verify user is a **teacher** in the course via `enrollmentService.isTeacherInCourse(userId, courseId)`. Throw forbidden if not.
2. Fetch the `Course` entity from `courseRepo`. Throw not found if missing.
3. Determine `orderIndex`:
   - If `request.orderIndex` is **not null**, use it directly.
   - If `request.orderIndex` is **null**, auto-assign: `moduleRepo.findMaxOrderIndexByCourseId(courseId)?.plus(1) ?: 0`.
4. Create a new `Module` entity with the course reference, title, and computed orderIndex.
5. Save via `moduleRepo.save(module)`.
6. Map to `ModuleResponse` via `moduleMapper.toResponse(module)` and return.

#### getModuleById(moduleId, userId)
1. Fetch the `Module` entity from `moduleRepo`. Throw not found if missing.
2. Derive `courseId` from `module.course.id`.
3. Verify user is **enrolled** in the course via `enrollmentService.isEnrolledInCourse(userId, courseId)`. Throw forbidden if not.
4. Compute `lessonCount` via `lessonRepo.countByModule_Id(moduleId)`.
5. Map to `ModuleResponse` with `lessonCount` and return.

#### updateModule(moduleId, userId, request)
1. Fetch the `Module` entity from `moduleRepo`. Throw not found if missing.
2. Derive `courseId` from `module.course.id`.
3. Verify user is a **teacher** in the course. Throw forbidden if not.
4. If `request.title` is not null, update `module.title`.
5. If `request.orderIndex` is not null, update `module.orderIndex`.
6. Save via `moduleRepo.save(module)`.
7. Map to `ModuleResponse` and return.

#### deleteModule(moduleId, userId)
1. Fetch the `Module` entity from `moduleRepo`. Throw not found if missing.
2. Derive `courseId` from `module.course.id`.
3. Verify user is a **teacher** in the course. Throw forbidden if not.
4. Delete via `moduleRepo.delete(module)` (triggers `@SQLDelete` soft delete).
5. Return `Mono.empty()`.

#### listCourseModules(courseId, userId)
1. Verify user is **enrolled** in the course. Throw forbidden if not.
2. Fetch all modules via `moduleRepo.findByCourse_IdOrderByOrderIndexAsc(courseId)`.
3. Map each to `ModuleResponse` via `moduleMapper.toResponse(module)`.
4. Return as `Flux`.

## 5. Controller

- **Annotations:** `@RestController`, `@RequestMapping("/v1")`

| Method | Path | Function Signature | Response Status |
|--------|------|--------------------|-----------------|
| `POST` | `/v1/courses/{courseId}/modules` | `createModule(@PathVariable courseId: String, @RequestHeader("userId") userId: String, @RequestBody request: CreateModuleRequest)` | `201 Created` |
| `GET` | `/v1/courses/{courseId}/modules` | `listCourseModules(@PathVariable courseId: String, @RequestHeader("userId") userId: String)` | `200 OK` |
| `GET` | `/v1/modules/{moduleId}` | `getModule(@PathVariable moduleId: String, @RequestHeader("userId") userId: String)` | `200 OK` |
| `PATCH` | `/v1/modules/{moduleId}` | `updateModule(@PathVariable moduleId: String, @RequestHeader("userId") userId: String, @RequestBody request: UpdateModuleRequest)` | `200 OK` |
| `DELETE` | `/v1/modules/{moduleId}` | `deleteModule(@PathVariable moduleId: String, @RequestHeader("userId") userId: String)` | `204 No Content` |

### Controller annotations per endpoint

- `POST /v1/courses/{courseId}/modules` -> `@PostMapping("/courses/{courseId}/modules")` with `@ResponseStatus(HttpStatus.CREATED)`
- `GET /v1/courses/{courseId}/modules` -> `@GetMapping("/courses/{courseId}/modules")`
- `GET /v1/modules/{moduleId}` -> `@GetMapping("/modules/{moduleId}")`
- `PATCH /v1/modules/{moduleId}` -> `@PatchMapping("/modules/{moduleId}")`
- `DELETE /v1/modules/{moduleId}` -> `@DeleteMapping("/modules/{moduleId}")` with `@ResponseStatus(HttpStatus.NO_CONTENT)`

## 6. Business Rules

1. **Teacher-only for mutations:** Only users with the teacher role in the course can create, update, or delete modules.
2. **Enrolled users can view:** Any user enrolled in the course (teacher or student) can retrieve individual modules or list all modules for a course.
3. **orderIndex auto-assignment:** When `orderIndex` is `null` in the create request, it is automatically assigned as `MAX(orderIndex) + 1` for the course, or `0` if no modules exist yet.
4. **Unique constraint on (courseId, orderIndex):** The database enforces that no two modules in the same course can share the same `orderIndex`. Violations result in a constraint error.
5. **Soft delete:** Modules are soft-deleted via `@SQLDelete` with inline SQL timestamp function `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` — NOT a bind parameter `?` for the timestamp, because Hibernate 6.x only passes the entity ID. `@Where(clause = "deleted_at IS NULL")` ensures soft-deleted modules are excluded from all standard queries.
6. **@PreUpdate lifecycle:** The `updatedAt` field is automatically refreshed to the current epoch millisecond timestamp before every update.
