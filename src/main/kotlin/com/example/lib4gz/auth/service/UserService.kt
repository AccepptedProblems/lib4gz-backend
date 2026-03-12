package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface UserService {
    fun grantTeacherRole(requesterId: String, targetUserId: String): Mono<UserResponse>
}

@Service
@Transactional
class UserServiceImpl(
    private val userRepo: UserRepo
) : UserService {

    override fun grantTeacherRole(requesterId: String, targetUserId: String): Mono<UserResponse> {
        return Mono.fromCallable {
            val requester = userRepo.findById(requesterId).orElseThrow {
                ResourceNotFoundException("User not found with id: $requesterId")
            }

            if (!requester.isAdmin()) {
                throw UnauthorizedException("Only users with ADMIN role can grant teacher role")
            }

            val targetUser = userRepo.findById(targetUserId).orElseThrow {
                ResourceNotFoundException("User not found with id: $targetUserId")
            }

            if (targetUser.hasRole(UserRole.TEACHER)) {
                throw BusinessException("User already has the TEACHER role")
            }

            targetUser.roles.add(UserRole.TEACHER)
            val savedUser = userRepo.save(targetUser)
            savedUser.toResponse()
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
