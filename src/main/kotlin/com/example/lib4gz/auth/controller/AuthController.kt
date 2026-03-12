package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.LoginReq
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.UserCreationRequest
import com.example.lib4gz.auth.model.payload.UserResponse
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

    }
}