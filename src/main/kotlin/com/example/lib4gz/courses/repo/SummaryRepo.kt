package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Summary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SummaryRepo : JpaRepository<Summary, String> {

    fun findByLesson_Id(lessonId: String): Summary?

    fun existsByLesson_Id(lessonId: String): Boolean

    fun deleteByLesson_Id(lessonId: String)
}