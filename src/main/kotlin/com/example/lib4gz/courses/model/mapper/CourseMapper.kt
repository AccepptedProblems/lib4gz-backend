package com.example.lib4gz.courses.model.mapper

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.courses.model.entity.*
import com.example.lib4gz.courses.model.payload.*
import org.springframework.stereotype.Component

@Component
class CourseMapper {

    companion object {
        fun toResponse(
            course: Course,
            moduleCount: Int? = null,
            enrollmentCount: Int? = null,
            myEnrollment: EnrollmentSummary? = null
        ): CourseResponse {
            return CourseResponse(
                id = course.id,
                code = course.code,
                title = course.title,
                description = course.description,
                visibility = course.visibility,
                createdBy = toUserSummary(course.createdBy),
                settings = course.settings,
                createdAt = course.createdAt,
                updatedAt = course.updatedAt,
                moduleCount = moduleCount,
                enrollmentCount = enrollmentCount,
                myEnrollment = myEnrollment
            )
        }

        fun toUserSummary(user: User): UserSummary {
            return UserSummary(
                id = user.id,
                name = user.name,
                email = user.email,
                avatarUrl = user.avatarUrl
            )
        }
    }
}

@Component
class EnrollmentMapper {

    fun toResponse(enrollment: Enrollment, courseName: String): EnrollmentResponse {
        return EnrollmentResponse(
            id = enrollment.id,
            courseId = enrollment.course.id,
            courseName = courseName,
            user = UserSummary(
                id = enrollment.user.id,
                name = enrollment.user.name,
                email = enrollment.user.email,
                avatarUrl = enrollment.user.avatarUrl
            ),
            role = enrollment.role,
            status = enrollment.status,
            joinedAt = enrollment.joinedAt,
            createdAt = enrollment.createdAt,
            updatedAt = enrollment.updatedAt
        )
    }

    fun toSummary(enrollment: Enrollment): EnrollmentSummary {
        return EnrollmentSummary(
            id = enrollment.id,
            role = enrollment.role,
            status = enrollment.status,
            joinedAt = enrollment.joinedAt
        )
    }
}

@Component
class ModuleMapper {

    fun toResponse(module: Module, lessonCount: Int? = null): ModuleResponse {
        return ModuleResponse(
            id = module.id,
            courseId = module.course.id,
            title = module.title,
            orderIndex = module.orderIndex,
            lessonCount = lessonCount,
            createdAt = module.createdAt,
            updatedAt = module.updatedAt
        )
    }

    fun toSummary(module: Module): ModuleSummary {
        return ModuleSummary(
            id = module.id,
            title = module.title,
            orderIndex = module.orderIndex
        )
    }
}

/**
 * Maps Lesson entity to LessonResponse.
 *
 * The `toResponse(...)` overload accepts optional expand-fields (`moduleSummary`,
 * `summary`, `exercises`). Callers only pass the fields they want to expose;
 * keeping a single map function avoids parallel "to*Response" methods per expand
 * combination while preserving the Single Responsibility of mapping.
 */
@Component
class LessonMapper {

    fun toResponse(
        lesson: Lesson,
        hasSummary: Boolean,
        exerciseCount: Int? = null,
        moduleTitle: String? = null,
        moduleSummary: ModuleSummary? = null,
        summary: SummaryResponse? = null,
        exercises: List<com.example.lib4gz.exercise.model.payload.ExerciseResponse>? = null
    ): LessonResponse {
        return LessonResponse(
            id = lesson.id,
            moduleId = lesson.module.id,
            title = lesson.title,
            orderIndex = lesson.orderIndex,
            hasSummary = hasSummary,
            exerciseCount = exerciseCount,
            createdAt = lesson.createdAt,
            updatedAt = lesson.updatedAt,
            moduleTitle = moduleTitle ?: lesson.module.title,
            module = moduleSummary,
            summary = summary,
            exercises = exercises
        )
    }
}

@Component
class SummaryMapper {

    fun toResponse(summary: Summary): SummaryResponse {
        return SummaryResponse(
            id = summary.id,
            lessonId = summary.lesson.id,
            content = summary.content,
            editedBy = UserSummary(
                id = summary.editedBy.id,
                name = summary.editedBy.name,
                email = summary.editedBy.email,
                avatarUrl = summary.editedBy.avatarUrl
            ),
            version = summary.version,
            createdAt = summary.createdAt,
            updatedAt = summary.updatedAt
        )
    }
}
