package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.LoginReq
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.UserCreationRequest
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.auth.repo.UserRepo
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

interface AuthService {
    fun register(userCreationRequest: UserCreationRequest): Mono<UserResponse>
    fun auth(loginReq: LoginReq): Mono<LoginResponse>
}

@Service
class AuthServiceImpl(
    private val userRepo: UserRepo,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider
): AuthService {
    override fun register(userCreationRequest: UserCreationRequest): Mono<UserResponse> {
        return Mono.fromCallable {
            // Check if username already exists
            if (userRepo.existsByUsername(userCreationRequest.username)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")
            }

            // Check if email already exists
            if (userRepo.existsByEmail(userCreationRequest.email)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
            }

            val user = userCreationRequest.toEntity().copy(
                password = passwordEncoder.encode(userCreationRequest.password),
                roles = mutableSetOf(UserRole.USER)
            )

            val savedUser = userRepo.save(user)
            return@fromCallable savedUser.toResponse()
        }
    }

    override fun auth(loginReq: LoginReq): Mono<LoginResponse> {
        return Mono.fromCallable {
            val user = userRepo.findByUsername(loginReq.username)
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")

            if (!passwordEncoder.matches(loginReq.password, user.password)) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
            }

            val token = jwtProvider.generateAccessToken(user)

            LoginResponse(
                user = user.toResponse(),
                accessToken = token
            )
        }
    }

}