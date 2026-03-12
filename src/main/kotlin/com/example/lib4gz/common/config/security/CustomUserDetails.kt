package com.example.lib4gz.common.config.security

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserStatus
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.UserResponse
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class CustomUserDetails(private val user: User): UserDetails {

    fun getUser(): UserResponse {
        return user.toResponse()
    }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority>? {
        return user.roles.map { role ->
            GrantedAuthority { role.toString() }
        }.toMutableList()
    }


    override fun getPassword(): String {
        return user.password ?: ""
    }

    override fun getUsername(): String {
        return user.email
    }

    override fun isAccountNonExpired(): Boolean {
        return true
    }

    override fun isAccountNonLocked(): Boolean {
        return true
    }

    override fun isCredentialsNonExpired(): Boolean {
        return true
    }

    override fun isEnabled(): Boolean {
        return user.status == UserStatus.ACTIVE
    }
}