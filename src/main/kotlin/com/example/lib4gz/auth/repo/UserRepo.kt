package com.example.lib4gz.auth.repo

import com.example.lib4gz.auth.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepo : JpaRepository<User, String> {
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun findByEmailIgnoreCase(email: String): User?
    fun findByGoogleId(googleId: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByEmailIgnoreCaseAndIdNot(email: String, id: String): Boolean
}
