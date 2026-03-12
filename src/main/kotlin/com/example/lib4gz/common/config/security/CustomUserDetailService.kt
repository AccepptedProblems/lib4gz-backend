package com.example.lib4gz.common.config.security

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.repo.UserRepo
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ExecutionException

@Service
class CustomUserDetailService(private val userRepo: UserRepo): UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        return try {
            val user: User = userRepo.findByUsername(username) ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "User not found"
            )
            CustomUserDetails(user)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }
}