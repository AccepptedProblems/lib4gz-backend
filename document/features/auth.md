# Authentication Feature

## 1. Overview

Authentication feature handling user registration, login, and JWT token management. Provides endpoints for creating new user accounts, authenticating with username/password, and receiving JWT access tokens. Auth endpoints are publicly accessible (no authentication required) as defined in security.json permittedEndpoints.

---

## 2. Models

> Full model definitions: [models/auth.yaml](../models/auth.yaml)

### Entity
- **User** — `users` table, soft delete via `@SQLDelete` (no `@Where`)

### Enums
- **UserRole**: ADMIN, USER, TEACHER
- **UserStatus**: ACTIVE, INACTIVE, BANNED

### DTOs
- **UserCreationRequest** — register payload (username, email, password) with `toEntity()` method
- **UserResponse** — id, username, email
- **LoginReq** — username, password
- **Token** — token, expiredAt
- **LoginResponse** — user (UserResponse) + accessToken (Token)

### Mapper
- **UserMapper** — extension function `User.toResponse(): UserResponse`

---

## 3. Repository

**Package:** `com.example.lib4gz.auth.repo`
**File:** `UserRepo.kt`

```kotlin
package com.example.lib4gz.auth.repo

import com.example.lib4gz.auth.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepo : JpaRepository<User, String> {
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
```

---

## 4. Service

**Package:** `com.example.lib4gz.auth.service`
**File:** `AuthService.kt` (interface) and `AuthServiceImpl.kt` (implementation)

### Interface

```kotlin
package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.payload.*
import reactor.core.publisher.Mono

interface AuthService {
    fun register(userCreationRequest: UserCreationRequest): Mono<UserResponse>
    fun auth(loginReq: LoginReq): Mono<LoginResponse>
}
```

### Implementation

```kotlin
package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.*
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.security.JwtProvider
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class AuthServiceImpl(
    private val userRepo: UserRepo,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider
) : AuthService {

    override fun register(userCreationRequest: UserCreationRequest): Mono<UserResponse> {
        if (userRepo.existsByUsername(userCreationRequest.username)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")
        }
        if (userRepo.existsByEmail(userCreationRequest.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
        }

        val user = userCreationRequest.toEntity().copy(
            password = passwordEncoder.encode(userCreationRequest.password)
        )

        val savedUser = userRepo.save(user)
        return Mono.just(savedUser.toResponse())
    }

    override fun auth(loginReq: LoginReq): Mono<LoginResponse> {
        val user = userRepo.findByUsername(loginReq.username)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!passwordEncoder.matches(loginReq.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        val accessToken = jwtProvider.generateToken(user)

        return Mono.just(
            LoginResponse(
                user = user.toResponse(),
                accessToken = accessToken
            )
        )
    }
}
```

**Business logic details:**
- `register()`: First checks `existsByUsername` -- throws 409 CONFLICT if true. Then checks `existsByEmail` -- throws 409 CONFLICT if true. Creates user entity via `userCreationRequest.toEntity()` then `.copy(password = passwordEncoder.encode(userCreationRequest.password))` to set the BCrypt-encoded password. Saves to repo. Returns `toResponse()`.
- `auth()`: Finds user by username via `userRepo.findByUsername()` -- throws 401 UNAUTHORIZED if null. Checks password via `passwordEncoder.matches(loginReq.password, user.password)` -- throws 401 UNAUTHORIZED if false. Generates access token via `jwtProvider.generateToken(user)`. Returns `LoginResponse` with user response and access token.

---

## 5. Controller

**Package:** `com.example.lib4gz.auth.controller`
**File:** `AuthController.kt`

### Endpoint Table

| Method | Path                   | Function       | Request Body          | Response              | HTTP Status |
|--------|------------------------|----------------|-----------------------|-----------------------|-------------|
| POST   | /v1/auth/register      | createUser     | UserCreationRequest   | Mono\<UserResponse\>  | 200         |
| POST   | /v1/auth/login         | login          | LoginReq              | Mono\<LoginResponse\> | 200         |
| POST   | /v1/auth/refresh-token | refreshToken   | -                     | Unit                  | 200         |

### Code

```kotlin
package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.*
import com.example.lib4gz.auth.service.AuthService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun createUser(@RequestBody userReq: UserCreationRequest): Mono<UserResponse> {
        return authService.register(userReq)
    }

    @PostMapping("/login")
    fun login(@RequestBody loginReq: LoginReq): Mono<LoginResponse> {
        return authService.auth(loginReq)
    }

    @PostMapping("/refresh-token")
    fun refreshToken() {
        // Not implemented
    }
}
```

---

## 6. Business Rules

1. **Username uniqueness:** Username must be unique across all users. Returns HTTP 409 CONFLICT if a user with the same username already exists.
2. **Email uniqueness:** Email must be unique across all users. Returns HTTP 409 CONFLICT if a user with the same email already exists.
3. **Password encoding:** Passwords are encoded with BCrypt via Spring's `PasswordEncoder` before storage. The raw password is never persisted.
4. **Login uses username:** Authentication is performed via username (not email). The `LoginReq` payload accepts a `username` field.
5. **Public endpoints:** All auth endpoints (`/v1/auth/register`, `/v1/auth/login`, `/v1/auth/refresh-token`) are listed in `security.json` as `permittedEndpoints` -- no authentication/JWT is required to access them.
6. **Soft delete:** The User entity uses `@SQLDelete` with inline SQL timestamp function `(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint` — NOT a bind parameter `?` for the timestamp, because Hibernate 6.x only passes the entity ID. Unlike other soft-deleted entities, User does NOT have `@Where(clause = "deleted_at IS NULL")`, meaning soft-deleted users can still appear in queries unless explicitly filtered.
7. **Default roles:** New users are created with `mutableSetOf(UserRole.USER)` by default.
8. **Default status:** New users are created with `UserStatus.ACTIVE` by default.
10. **TEACHER role:** The `TEACHER` global role grants the ability to create courses. Users without `UserRole.TEACHER` receive HTTP 403 FORBIDDEN when calling `POST /v1/courses`. This is separate from the per-course `EnrollmentRole.TEACHER` which governs in-course permissions.
9. **Refresh token endpoint:** The `/v1/auth/refresh-token` endpoint exists but is not implemented (empty body, returns 200).
