package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.CourseResourceFreshness
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.entity.FreshnessResourceType
import com.example.lib4gz.courses.model.entity.Visibility
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.CourseResourceFreshnessRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class FreshnessServiceImplTest {

    private lateinit var courseRepo: CourseRepo
    private lateinit var enrollmentRepo: EnrollmentRepo
    private lateinit var freshnessRepo: CourseResourceFreshnessRepo
    private lateinit var service: FreshnessServiceImpl

    private val creator = User(id = "usr_creator", email = "t@t.t", username = "teacher", name = "Teacher")
    private val student = User(id = "usr_student", email = "s@s.s", username = "student", name = "Student")

    @BeforeEach
    fun setUp() {
        courseRepo = mock(CourseRepo::class.java)
        enrollmentRepo = mock(EnrollmentRepo::class.java)
        freshnessRepo = mock(CourseResourceFreshnessRepo::class.java)
        service = FreshnessServiceImpl(courseRepo, enrollmentRepo, freshnessRepo)
    }

    private fun course(visibility: Visibility) = Course(
        id = "crs_1", title = "Course", visibility = visibility, createdBy = creator
    )

    private fun givenCourse(course: Course) {
        `when`(courseRepo.findById("crs_1")).thenReturn(Optional.of(course))
    }

    @Test
    fun `zero-fills all resource types when no rows exist`() {
        givenCourse(course(Visibility.PUBLIC))
        `when`(freshnessRepo.findByCourseId("crs_1")).thenReturn(emptyList())

        val response = service.getLastUpdates("crs_1", "usr_student").block()!!

        assertEquals("crs_1", response.courseId)
        assertEquals(FreshnessResourceType.entries.size, response.lastUpdates.size)
        FreshnessResourceType.entries.forEach { assertEquals(0L, response.lastUpdates[it]) }
    }

    @Test
    fun `merges existing rows and zero-fills the rest`() {
        givenCourse(course(Visibility.PUBLIC))
        `when`(freshnessRepo.findByCourseId("crs_1")).thenReturn(
            listOf(
                CourseResourceFreshness(courseId = "crs_1", resourceType = FreshnessResourceType.LESSON, lastUpdate = 123L),
                CourseResourceFreshness(courseId = "crs_1", resourceType = FreshnessResourceType.SYLLABUS, lastUpdate = 456L)
            )
        )

        val response = service.getLastUpdates("crs_1", "usr_student").block()!!

        assertEquals(123L, response.lastUpdates[FreshnessResourceType.LESSON])
        assertEquals(456L, response.lastUpdates[FreshnessResourceType.SYLLABUS])
        assertEquals(0L, response.lastUpdates[FreshnessResourceType.SUMMARY])
        assertEquals(0L, response.lastUpdates[FreshnessResourceType.EXERCISE])
        assertEquals(0L, response.lastUpdates[FreshnessResourceType.SUBMISSION])
    }

    @Test
    fun `throws ResourceNotFoundException for a missing course`() {
        `when`(courseRepo.findById("crs_1")).thenReturn(Optional.empty())

        assertThrows(ResourceNotFoundException::class.java) {
            service.getLastUpdates("crs_1", "usr_student").block()
        }
    }

    @Test
    fun `denies a non-enrolled non-creator on a private course`() {
        givenCourse(course(Visibility.PRIVATE))
        `when`(enrollmentRepo.findByCourse_IdAndUser_Id("crs_1", "usr_student")).thenReturn(null)

        assertThrows(UnauthorizedException::class.java) {
            service.getLastUpdates("crs_1", "usr_student").block()
        }
    }

    @Test
    fun `denies a pending enrollment on a private course`() {
        val privateCourse = course(Visibility.PRIVATE)
        givenCourse(privateCourse)
        `when`(enrollmentRepo.findByCourse_IdAndUser_Id("crs_1", "usr_student")).thenReturn(
            Enrollment(course = privateCourse, user = student, role = EnrollmentRole.LEARNER, status = EnrollmentStatus.PENDING)
        )

        assertThrows(UnauthorizedException::class.java) {
            service.getLastUpdates("crs_1", "usr_student").block()
        }
    }

    @Test
    fun `allows an active enrollment on a private course`() {
        val privateCourse = course(Visibility.PRIVATE)
        givenCourse(privateCourse)
        `when`(enrollmentRepo.findByCourse_IdAndUser_Id("crs_1", "usr_student")).thenReturn(
            Enrollment(course = privateCourse, user = student, role = EnrollmentRole.LEARNER, status = EnrollmentStatus.ACTIVE)
        )
        `when`(freshnessRepo.findByCourseId("crs_1")).thenReturn(emptyList())

        val response = service.getLastUpdates("crs_1", "usr_student").block()!!
        assertEquals("crs_1", response.courseId)
    }

    @Test
    fun `allows a non-enrolled viewer on a public course`() {
        givenCourse(course(Visibility.PUBLIC))
        `when`(enrollmentRepo.findByCourse_IdAndUser_Id("crs_1", "usr_student")).thenReturn(null)
        `when`(freshnessRepo.findByCourseId("crs_1")).thenReturn(emptyList())

        val response = service.getLastUpdates("crs_1", "usr_student").block()!!
        assertEquals("crs_1", response.courseId)
    }

    @Test
    fun `allows the creator regardless of visibility and enrollment`() {
        givenCourse(course(Visibility.PRIVATE))
        `when`(enrollmentRepo.findByCourse_IdAndUser_Id("crs_1", "usr_creator")).thenReturn(null)
        `when`(freshnessRepo.findByCourseId("crs_1")).thenReturn(emptyList())

        val response = service.getLastUpdates("crs_1", "usr_creator").block()!!
        assertEquals("crs_1", response.courseId)
    }
}
