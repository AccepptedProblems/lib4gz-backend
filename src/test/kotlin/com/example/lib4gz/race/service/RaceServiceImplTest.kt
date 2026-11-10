package com.example.lib4gz.race.service

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.auth.service.anyValue
import com.example.lib4gz.auth.service.passThroughTransactionTemplate
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.Module
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.race.model.entity.RaceQuestion
import com.example.lib4gz.race.model.entity.RaceResult
import com.example.lib4gz.race.model.mapper.RaceMapper
import com.example.lib4gz.race.model.payload.CreateRaceResultRequest
import com.example.lib4gz.race.model.payload.RaceQuestionItem
import com.example.lib4gz.race.model.payload.ReplaceRaceQuestionsRequest
import com.example.lib4gz.race.repo.RaceQuestionRepo
import com.example.lib4gz.race.repo.RaceResultRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RaceServiceImplTest {

    private val raceQuestionRepo = mock(RaceQuestionRepo::class.java)
    private val raceResultRepo = mock(RaceResultRepo::class.java)
    private val courseRepo = mock(CourseRepo::class.java)
    private val moduleRepo = mock(ModuleRepo::class.java)
    private val userRepo = mock(UserRepo::class.java)
    private val enrollmentService = mock(EnrollmentService::class.java)
    private val service = RaceServiceImpl(
        raceQuestionRepo = raceQuestionRepo,
        raceResultRepo = raceResultRepo,
        courseRepo = courseRepo,
        moduleRepo = moduleRepo,
        userRepo = userRepo,
        enrollmentService = enrollmentService,
        raceMapper = RaceMapper(),
        transactionTemplate = passThroughTransactionTemplate()
    )

    private val teacher = User(id = "usr_teacher", email = "t@x.com", username = "teacher", name = "Teacher")
    private val student = User(id = "usr_student", email = "s@x.com", username = "student", name = "Student")
    private val course = Course(id = "crs_1", title = "Course", createdBy = teacher)
    private val moduleA = Module(id = "mod_a", course = course, title = "A", orderIndex = 0)

    @BeforeEach
    fun setUp() {
        `when`(courseRepo.existsById("crs_1")).thenReturn(true)
        `when`(moduleRepo.findByCourse_Id("crs_1")).thenReturn(listOf(moduleA))
        `when`(moduleRepo.existsByIdAndCourse_Id("mod_a", "crs_1")).thenReturn(true)
        `when`(enrollmentService.isTeacherInCourse("crs_1", "usr_teacher")).thenReturn(true)
        `when`(enrollmentService.isEnrolledInCourse("crs_1", "usr_teacher")).thenReturn(true)
        `when`(enrollmentService.isEnrolledInCourse("crs_1", "usr_student")).thenReturn(true)
        @Suppress("UNCHECKED_CAST")
        `when`(raceQuestionRepo.saveAll(anyValue<List<RaceQuestion>>()))
            .thenAnswer { (it.arguments[0] as Iterable<RaceQuestion>).toList() }
        `when`(raceResultRepo.save(anyValue<RaceResult>())).then(returnsFirstArg<RaceResult>())
    }

    private fun item(
        id: String? = null,
        moduleId: String = "mod_a",
        options: List<String> = listOf("Paris", "Rome"),
        correctIndex: Int = 0
    ) = RaceQuestionItem(id = id, moduleId = moduleId, content = "Capital of France?", options = options, correctIndex = correctIndex)

    private fun replace(vararg items: RaceQuestionItem, userId: String = "usr_teacher") =
        service.replaceQuestions("crs_1", userId, ReplaceRaceQuestionsRequest(items.toList())).collectList().block()!!

    @Test
    fun `a student cannot replace the bank`() {
        assertThrows(UnauthorizedException::class.java) { replace(item(), userId = "usr_student") }
        verify(raceQuestionRepo, never()).saveAll(anyValue<List<RaceQuestion>>())
    }

    @Test
    fun `one option is refused`() {
        val ex = assertThrows(BadRequestException::class.java) { replace(item(options = listOf("Paris"))) }
        assertEquals("Question 1 needs between 2 and 6 options; it has 1.", ex.message)
    }

    @Test
    fun `seven options are refused`() {
        val seven = listOf("a", "b", "c", "d", "e", "f", "g")
        val ex = assertThrows(BadRequestException::class.java) { replace(item(), item(options = seven)) }
        assertEquals("Question 2 needs between 2 and 6 options; it has 7.", ex.message)
    }

    @Test
    fun `a correct index outside the options is refused`() {
        assertThrows(BadRequestException::class.java) { replace(item(correctIndex = 2)) }
        assertThrows(BadRequestException::class.java) { replace(item(correctIndex = -1)) }
    }

    @Test
    fun `a module of another course is refused`() {
        val ex = assertThrows(BadRequestException::class.java) { replace(item(moduleId = "mod_other")) }
        assertEquals("Question 1 is assigned to a module that is not in this course.", ex.message)
    }

    @Test
    fun `replace hides a question whose id is absent and keeps one whose id is present`() {
        val kept = RaceQuestion(
            id = "rqn_kept", courseId = "crs_1", moduleId = "mod_a", content = "old",
            options = listOf(mapOf("text" to "x"), mapOf("text" to "y")), correctIndex = 1, orderIndex = 0
        )
        val dropped = kept.copy(id = "rqn_dropped", orderIndex = 1)
        `when`(raceQuestionRepo.findByCourseId("crs_1")).thenReturn(listOf(kept, dropped))

        val saved = replace(item(), item(id = "rqn_kept", options = listOf("x", "y", "z"), correctIndex = 2))

        verify(raceQuestionRepo).delete(dropped)
        verify(raceQuestionRepo, never()).delete(kept)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<RaceQuestion>>
        verify(raceQuestionRepo).saveAll(captor.capture())
        val written = captor.value
        assertEquals(2, written.size)
        assertTrue(written[0].id.startsWith("rqn_"))
        assertEquals(0, written[0].orderIndex)
        assertEquals("rqn_kept", written[1].id)
        assertEquals(1, written[1].orderIndex)
        assertEquals("Capital of France?", written[1].content)
        assertEquals(2, written[1].correctIndex)

        assertEquals(listOf("x", "y", "z"), saved[1].options)
        assertEquals(listOf(0, 1), saved.map { it.orderIndex })
    }

    @Test
    fun `an id the course no longer holds is refused`() {
        `when`(raceQuestionRepo.findByCourseId("crs_1")).thenReturn(emptyList())

        val ex = assertThrows(BadRequestException::class.java) { replace(item(id = "rqn_stale")) }

        assertEquals("Question 1 is no longer in this course's bank. Reload the page and save again.", ex.message)
        verify(raceQuestionRepo, never()).saveAll(anyValue<List<RaceQuestion>>())
    }

    @Test
    fun `an id listed twice is refused`() {
        val ex = assertThrows(BadRequestException::class.java) { replace(item(id = "rqn_1"), item(id = "rqn_1")) }

        assertEquals("Question 2 is listed twice.", ex.message)
        verify(raceQuestionRepo, never()).findByCourseId("crs_1")
    }

    @Test
    fun `a result with a non-positive finish time or a negative count is refused`() {
        val good = CreateRaceResultRequest(moduleId = "mod_a", finishMs = 90_000, correctCount = 5, wrongCount = 1, seed = 7)

        assertThrows(BadRequestException::class.java) { service.saveResult("crs_1", "usr_student", good.copy(finishMs = 0)).block() }
        assertThrows(BadRequestException::class.java) { service.saveResult("crs_1", "usr_student", good.copy(correctCount = -1)).block() }
        assertThrows(BadRequestException::class.java) { service.saveResult("crs_1", "usr_student", good.copy(wrongCount = -1)).block() }
        verify(raceResultRepo, never()).save(anyValue<RaceResult>())
    }

    @Test
    fun `a result for a module of another course is refused`() {
        val request = CreateRaceResultRequest(moduleId = "mod_other", finishMs = 90_000, correctCount = 5, wrongCount = 1, seed = 7)

        val ex = assertThrows(BadRequestException::class.java) { service.saveResult("crs_1", "usr_student", request).block() }

        assertEquals("That module is not in this course.", ex.message)
        verify(raceResultRepo, never()).save(anyValue<RaceResult>())
    }

    @Test
    fun `a result for the whole course is saved for the caller`() {
        val request = CreateRaceResultRequest(moduleId = null, finishMs = 90_000, correctCount = 5, wrongCount = 1, seed = 7)

        val response = service.saveResult("crs_1", "usr_student", request).block()!!

        assertEquals("usr_student", response.userId)
        assertNull(response.moduleId)
        assertTrue(response.id.startsWith("rrs_"))
    }

    @Test
    fun `leaderboard returns the caller's best as mine`() {
        val teacherBest = RaceResult(userId = "usr_teacher", courseId = "crs_1", finishMs = 80_000, correctCount = 6, wrongCount = 0, seed = 1)
        val studentBest = RaceResult(userId = "usr_student", courseId = "crs_1", finishMs = 95_000, correctCount = 4, wrongCount = 2, seed = 2)
        `when`(raceResultRepo.findBestPerUserInCourse("crs_1")).thenReturn(listOf(teacherBest, studentBest))
        `when`(raceResultRepo.findTopByUserIdAndCourseIdAndModuleIdIsNullOrderByFinishMsAscCreatedAtAsc("usr_student", "crs_1"))
            .thenReturn(studentBest)
        student.raceAppearance = mapOf("characterId" to "chr_bolt", "kartId" to "krt_classic")
        `when`(userRepo.findAllById(anyValue<Iterable<String>>())).thenReturn(listOf(teacher, student))

        val board = service.leaderboard("crs_1", null, "usr_student").block()!!

        assertEquals(listOf("usr_teacher", "usr_student"), board.rows.map { it.userId })
        assertEquals("teacher", board.rows[0].username)
        assertEquals("usr_student", board.mine!!.userId)
        assertEquals(95_000, board.mine!!.finishMs)
        assertEquals("chr_bolt", board.mine!!.appearance!!["characterId"])
    }

    @Test
    fun `leaderboard mine is null for a caller who never finished`() {
        `when`(raceResultRepo.findBestPerUserInModule("crs_1", "mod_a")).thenReturn(emptyList())
        `when`(userRepo.findAllById(anyValue<Iterable<String>>())).thenReturn(emptyList())

        val board = service.leaderboard("crs_1", "mod_a", "usr_student").block()!!

        assertTrue(board.rows.isEmpty())
        assertNull(board.mine)
    }
}
