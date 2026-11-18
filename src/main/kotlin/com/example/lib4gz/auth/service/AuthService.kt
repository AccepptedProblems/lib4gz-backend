package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.LoginReq
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.auth.repo.UserRepo
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface AuthService {
    fun auth(loginReq: LoginReq): Mono<LoginResponse>
}

@Service
class AuthServiceImpl(
    private val userRepo: UserRepo,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenService: RefreshTokenService
): AuthService {
    override fun auth(loginReq: LoginReq): Mono<LoginResponse> {
        return Mono.fromCallable {
            val login = loginReq.username.trim()
            val user = if (login.contains('@')) userRepo.findByEmailIgnoreCase(login) else userRepo.findByUsername(login)

            if (user?.password == null || !passwordEncoder.matches(loginReq.password, user.password)) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
            }

            LoginResponse(
                user = user.toResponse(),
                accessToken = jwtProvider.generateAccessToken(user),
                refreshToken = refreshTokenService.issue(user.id)
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

}