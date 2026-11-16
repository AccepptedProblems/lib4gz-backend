package com.example.lib4gz.race.service

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.auth.service.anyValue
import com.example.lib4gz.auth.service.passThroughTransactionTemplate
import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.Module
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.race.model.entity.RaceQuestion
import com.example.lib4gz.race.model.entity.RaceResult
import com.example.lib4gz.race.model.mapper.RaceMapper
import com.example.lib4gz.race.model.entity.Race
import com.example.lib4gz.race.model.payload.AnswerRequest
import com.example.lib4gz.race.model.payload.AnswerResponse
import com.example.lib4gz.race.model.payload.FinishRaceRequest
import com.example.lib4gz.race.model.payload.RaceQuestionItem
import com.example.lib4gz.race.model.payload.StartRaceRequest
import com.example.lib4gz.race.model.payload.ReplaceRaceQuestionsRequest
import com.example.lib4gz.race.repo.RaceQuestionRepo
import com.example.lib4gz.race.repo.RaceRepo
import com.example.lib4gz.race.repo.RaceResultRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.mockito.Mockito.`when`

class RaceServiceImplTest {

    private val raceQuestionRepo = mock(RaceQuestionRepo::class.java)
    private val raceResultRepo = mock(RaceResultRepo::class.java)
    private val courseRepo = mock(CourseRepo::class.java)
    private val moduleRepo = mock(ModuleRepo::class.java)
    private val userRepo = mock(UserRepo::class.java)
    private val enrollmentService = mock(EnrollmentService::class.java)
    private val raceRepo = mock(RaceRepo::class.java)
    private val service = RaceServiceImpl(
        raceQuestionRepo = raceQuestionRepo,
        raceResultRepo = raceResultRepo,
        raceRepo = raceRepo,
        courseRepo = courseRepo,
        moduleRepo = moduleRepo,
        userRepo = userRepo,
        enrollmentService = enrollmentService,
        raceMapper = RaceMapper(),
        transactionTemplate = passThroughTransactionTemplate(),
        properties = RaceProperties(laps = 3, lapLength = 2640.0, topSpeed = 44.0, boostSeconds = 5.0, doubleMultiplier = 1.7, maxQuestions = 100)
    )

    private val teacher = User(id = "usr_teacher", email = "t@x.com", username = "teacher", name = "Teacher")
    private val student = User(id = "usr_student", email = "s@x.com", username = "student", name = "Student")
    private val course = Course(id = "crs_1", title = "Course", createdBy = teacher)
    private val moduleA = Module(id = "mod_a", course = course, title = "A", orderIndex = 0)

    @BeforeEach
    fun setUp() {
        `when`(courseRepo.existsById("crs_1")).thenReturn(true)
        `when`(moduleRepo.findByCourse_Id("crs_1")).thenReturn(listOf(moduleA))
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

    // ---- the question list ----

    private fun bankQuestion(id: String, options: List<String> = listOf("Paris", "Rome"), correctIndex: Int = 0) = RaceQuestion(
        id = id, courseId = "crs_1", moduleId = "mod_a", content = "Q $id",
        options = options.map { mapOf("text" to it) }, correctIndex = correctIndex, orderIndex = 0
    )

    @Test
    fun `a student's question list carries no options and no key, and a teacher's carries both`() {
        `when`(raceQuestionRepo.findByCourseIdOrderByOrderIndexAscCreatedAtAsc("crs_1"))
            .thenReturn(listOf(bankQuestion("rqn_1", correctIndex = 1)))

        val forStudent = service.listQuestions("crs_1", "usr_student").collectList().block()!!.single()
        val forTeacher = service.listQuestions("crs_1", "usr_teacher").collectList().block()!!.single()

        assertTrue(forStudent.options.isEmpty())
        assertNull(forStudent.correctIndex)
        assertEquals("mod_a", forStudent.moduleId)
        assertEquals(listOf("Paris", "Rome"), forTeacher.options)
        assertEquals(1, forTeacher.correctIndex)
    }

    @Test
    fun `the question list is refused to someone not enrolled`() {
        assertThrows(UnauthorizedException::class.java) { service.listQuestions("crs_1", "usr_stranger").collectList().block() }
    }

    // ---- start ----

    private fun start(count: Int, moduleId: String? = null, userId: String = "usr_student") =
        service.startRace("crs_1", userId, StartRaceRequest(moduleId = moduleId, questionCount = count)).block()!!

    private fun savedRaces(times: Int = 1): List<Race> {
        val captor = ArgumentCaptor.forClass(Race::class.java)
        verify(raceRepo, times(times)).save(captor.capture())
        return captor.allValues
    }

    private fun bank(vararg questions: RaceQuestion) {
        `when`(raceQuestionRepo.findByCourseId("crs_1")).thenReturn(questions.toList())
        `when`(raceRepo.save(anyValue<Race>())).then(returnsFirstArg<Race>())
    }

    @Test
    fun `start refuses a user who is not enrolled`() {
        assertThrows(UnauthorizedException::class.java) { start(24, userId = "usr_stranger") }
        verify(raceRepo, never()).save(anyValue<Race>())
    }

    @Test
    fun `start deals the asked count with at most four options, keeps the key on the server, and never repeats a question back to back`() {
        bank(
            bankQuestion("rqn_1", listOf("a", "b", "c", "d", "e", "RIGHT"), 5),
            bankQuestion("rqn_2", listOf("RIGHT", "x"), 0),
            bankQuestion("rqn_3", listOf("y", "RIGHT", "z"), 1)
        )

        val reply = start(24)
        val race = savedRaces().single()

        assertTrue(reply.raceId.startsWith("rce_"))
        assertEquals(24, reply.questions.size)
        assertEquals((0 until 24).toList(), reply.questions.map { it.seq })
        assertTrue(reply.questions.all { it.options.size <= 4 && "RIGHT" in it.options })
        assertEquals(4, reply.questions.first { it.questionId == "rqn_1" }.options.size)
        reply.questions.zipWithNext().forEach { (a, b) -> assertTrue(a.questionId != b.questionId) }
        // The stored key points at the right option as shown, and the stored deck is the one sent.
        assertEquals(reply.questions.map { it.questionId }, race.questionIds)
        reply.questions.forEachIndexed { seq, q -> assertEquals("RIGHT", q.options[race.answerKey[seq]]) }
        assertEquals(reply.seed, race.seed)
        assertEquals(0, race.nextSeq)
    }

    @Test
    fun `a six-option question shows the same four texts in every deal`() {
        bank(bankQuestion("rqn_1", listOf("a", "b", "RIGHT", "d", "e", "f"), 2))

        val shown = (1..30).map { start(1).questions.single().options.toSet() }.toSet()

        assertEquals(1, shown.size)
        assertEquals(4, shown.single().size)
        assertTrue("RIGHT" in shown.single())
    }

    @Test
    fun `an option text equal to the right one is never shown as a wrong one`() {
        bank(bankQuestion("rqn_1", listOf("RIGHT", "b", "RIGHT", "c", "b", "d"), 0))

        repeat(20) {
            val options = start(1).questions.single().options
            assertEquals(1, options.count { it == "RIGHT" })
            assertEquals(options.size, options.distinct().size)
        }
    }

    @Test
    fun `the options are shuffled in every deal`() {
        bank(bankQuestion("rqn_1", listOf("RIGHT", "w1", "w2", "w3"), 0))

        val shown = (1..30).map { start(1).questions.single().options }

        assertTrue(shown.toSet().size > 1, "the options are always shown in one order")
        assertTrue(shown.map { it.indexOf("RIGHT") }.toSet().size > 1, "the right option is always in one place")
    }

    @Test
    fun `the deck order changes from race to race`() {
        bank(*Array(5) { bankQuestion("rqn_$it", listOf("RIGHT", "w1"), 0) })

        val decks = (1..20).map { r -> start(5).questions.map { it.questionId } }

        assertTrue(decks.toSet().size > 1, "the deck is dealt in one fixed order")
    }

    @Test
    fun `start caps the count at the server's maximum`() {
        `when`(raceQuestionRepo.findByCourseIdAndModuleId("crs_1", "mod_a")).thenReturn(listOf(bankQuestion("rqn_1"), bankQuestion("rqn_2")))
        `when`(raceRepo.save(anyValue<Race>())).then(returnsFirstArg<Race>())

        assertEquals(100, start(5000, moduleId = "mod_a").questions.size)
    }

    @Test
    fun `start refuses a scope with no questions`() {
        `when`(raceQuestionRepo.findByCourseIdAndModuleId("crs_1", "mod_other")).thenReturn(emptyList())

        val ex = assertThrows(BadRequestException::class.java) { start(24, moduleId = "mod_other") }

        assertEquals("There are no race questions here yet.", ex.message)
        verify(raceRepo, never()).save(anyValue<Race>())
    }

    // ---- answer ----
    // The repo is stubbed as the database behaves: the race rce_1 exists, belongs to usr_student and
    // sits under crs_1, and both the lookup and the answer statement find it only for that user and
    // (for the statement) that course.

    private fun verdict(correct: Boolean, right: Int, wrong: Int) = object : RaceRepo.Verdict {
        override val correctCount = right
        override val wrongCount = wrong
        override val lastCorrect = correct
    }

    private fun finished(right: Int, wrong: Int, wrongSeqs: List<Int> = emptyList()) = object : RaceRepo.Finished {
        override val correctCount = right
        override val wrongCount = wrong
        // As Postgres prints a jsonb array.
        override val wrongSeqs = wrongSeqs.joinToString(", ", "[", "]")
    }

    private fun race(
        nextSeq: Int = 0,
        right: Int = 0,
        wrong: Int = 0,
        lastCorrect: Boolean? = null,
        startedAt: Long = System.currentTimeMillis() - 200_000,
        finishedAt: Long? = null
    ) = Race(
        id = "rce_1", userId = "usr_student", courseId = "crs_1", seed = 9,
        questionIds = List(10) { "rqn_$it" }, answerKey = List(10) { it % 4 },
        nextSeq = nextSeq, correctCount = right, wrongCount = wrong, lastCorrect = lastCorrect,
        startedAt = startedAt, finishedAt = finishedAt
    )

    /** Stubs the lookups to find [race] only by its own id and user, as the database would. */
    private fun exists(race: Race) {
        `when`(raceRepo.findByIdAndUserId(anyValue(), anyValue())).thenAnswer {
            race.takeIf { _ -> it.arguments[0] == race.id && it.arguments[1] == race.userId }
        }
        `when`(raceRepo.findById(anyValue())).thenAnswer { java.util.Optional.ofNullable(race.takeIf { _ -> it.arguments[0] == race.id }) }
        `when`(raceRepo.findAll()).thenReturn(listOf(race))
    }

    private fun answer(seq: Int, choice: Int?, userId: String = "usr_student", courseId: String = "crs_1") =
        service.answer(courseId, "rce_1", userId, AnswerRequest(seq, choice)).block()!!

    @Test
    fun `an answer is one update and nothing else`() {
        `when`(raceRepo.answer("rce_1", "usr_student", "crs_1", 0, 2)).thenReturn(verdict(true, 1, 0))

        val reply = answer(0, 2)

        assertEquals(AnswerResponse(seq = 0, correct = true, correctCount = 1, wrongCount = 0), reply)
        verify(raceRepo, never()).findByIdAndUserId(anyValue(), anyValue())
        verifyNoInteractions(enrollmentService)
    }

    @Test
    fun `a timeout is sent as a choice that matches no key`() {
        `when`(raceRepo.answer("rce_1", "usr_student", "crs_1", 0, -1)).thenReturn(verdict(false, 0, 1))

        assertEquals(false, answer(0, null).correct)
    }

    @Test
    fun `a resend of the previous question gets the stored verdict without counting twice`() {
        // The update refuses seq 3 because next_seq is already 4: the first send was counted.
        exists(race(nextSeq = 4, right = 3, wrong = 1, lastCorrect = true))

        val reply = answer(3, 1)

        assertEquals(AnswerResponse(seq = 3, correct = true, correctCount = 3, wrongCount = 1), reply)
        verify(raceRepo, never()).save(anyValue<Race>())
    }

    @Test
    fun `a retry of an earlier question is refused`() {
        exists(race(nextSeq = 4, right = 3, wrong = 1, lastCorrect = true))

        val ex = assertThrows(ResponseStatusException::class.java) { answer(1, 0) }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("That question has already been answered or skipped.", ex.reason)
    }

    @Test
    fun `another user's race is refused`() {
        exists(race(startedAt = System.currentTimeMillis() - 600_000))

        assertThrows(ResourceNotFoundException::class.java) { answer(0, 0, userId = "usr_teacher") }
        assertThrows(ResourceNotFoundException::class.java) {
            service.finishRace("crs_1", "rce_1", "usr_teacher", FinishRaceRequest(150_000)).block()
        }
        verify(raceRepo).answer("rce_1", "usr_teacher", "crs_1", 0, 0)
        verify(raceRepo, never()).markFinished(anyValue(), ArgumentMatchers.anyLong())
        verify(raceResultRepo, never()).save(anyValue<RaceResult>())
    }

    @Test
    fun `a race under another course is refused, even to its own racer`() {
        exists(race(nextSeq = 1, lastCorrect = true, startedAt = System.currentTimeMillis() - 600_000))

        // seq 0 would be a resend under crs_1.
        assertThrows(ResourceNotFoundException::class.java) { answer(0, 0, courseId = "crs_2") }
        assertThrows(ResourceNotFoundException::class.java) {
            service.finishRace("crs_2", "rce_1", "usr_student", FinishRaceRequest(150_000)).block()
        }
        verify(raceRepo).answer("rce_1", "usr_student", "crs_2", 0, 0)
        verify(raceRepo, never()).markFinished(anyValue(), ArgumentMatchers.anyLong())
    }

    @Test
    fun `a question past the deck and an option past the cards are refused`() {
        exists(race(nextSeq = 2, lastCorrect = false))

        assertEquals("That question is not part of this race.", assertThrows(BadRequestException::class.java) { answer(10, 0) }.message)
        assertThrows(BadRequestException::class.java) { answer(-1, 0) }
        assertThrows(BadRequestException::class.java) { answer(2, 4) }
    }

    // ---- finish ----
    // The floor with these numbers: 180 s with no boost, less 3.5 s per right answer, never under 105.9 s.

    private fun finish(finishMs: Int) =
        service.finishRace("crs_1", "rce_1", "usr_student", FinishRaceRequest(finishMs)).block()!!

    @Test
    fun `a finish exactly at the floor is accepted and one millisecond under is refused`() {
        exists(race(right = 10, startedAt = System.currentTimeMillis() - 600_000))
        `when`(raceRepo.markFinished(anyValue(), ArgumentMatchers.anyLong())).thenReturn(finished(10, 0))

        // 10 right: 180 - 35 = 145 s.
        val ex = assertThrows(BadRequestException::class.java) { finish(144_999) }
        assertEquals("That finish time is faster than your right answers allow.", ex.message)
        verify(raceResultRepo, never()).save(anyValue<RaceResult>())

        assertEquals(145_000, finish(145_000).finishMs)
    }

    @Test
    fun `the floor never drops under the no-boost time at Double Boost speed`() {
        exists(race(right = 40, startedAt = System.currentTimeMillis() - 600_000))

        assertThrows(BadRequestException::class.java) { finish(105_000) }
    }

    @Test
    fun `a finish longer than the race has been running is refused`() {
        exists(race(startedAt = System.currentTimeMillis() - 190_000))

        val ex = assertThrows(BadRequestException::class.java) { finish(200_000) }

        assertEquals("That finish time is longer than the race has been running.", ex.message)
        verify(raceResultRepo, never()).save(anyValue<RaceResult>())
    }

    @Test
    fun `a finish saves the counts and sends the key of the wrong numbers of its own finishing update`() {
        // Read with 10 right, 4 wrong and no wrong number recorded; answers landed before the finish
        // mark, which returns 11 right, 5 wrong and seq 6 wrong.
        exists(race(right = 10, wrong = 4))
        `when`(raceRepo.markFinished(anyValue(), ArgumentMatchers.anyLong())).thenReturn(finished(11, 5, listOf(6)))

        val result = finish(150_000)

        assertEquals(11, result.correctCount)
        assertEquals(5, result.wrongCount)
        assertEquals(150_000, result.finishMs)
        assertEquals(9, result.seed)
        assertEquals("usr_student", result.userId)
        assertEquals(listOf(null, null, null, null, null, null, 2, null, null, null), result.answerKey)
    }

    // The deck's key is `seq % 4`: [0, 1, 2, 3, 0, 1, 2, 3, 0, 1].

    /** Finishes a race of 10 whose finishing update returns [wrongSeqs]; returns the reply's key. */
    private fun keyAfterWrong(vararg wrongSeqs: Int, nextSeq: Int = 10): List<Int?> {
        exists(race(nextSeq = nextSeq))
        `when`(raceRepo.markFinished(anyValue(), ArgumentMatchers.anyLong()))
            .thenReturn(finished(nextSeq - wrongSeqs.size, wrongSeqs.size, wrongSeqs.toList()))
        return finish(190_000).answerKey
    }

    @Test
    fun `a finish with no wrong answer sends no key`() {
        assertEquals(List(10) { null }, keyAfterWrong())
    }

    @Test
    fun `a finish sends the key of exactly the wrong numbers`() {
        assertEquals(listOf(null, null, 2, null, null, null, null, 3, null, null), keyAfterWrong(2, 7))
    }

    @Test
    fun `a finish sends the key of the first four wrong numbers only`() {
        assertEquals(listOf(0, 1, null, null, 0, 1, null, null, null, null), keyAfterWrong(0, 1, 4, 5, 6, 9))
    }

    @Test
    fun `a number never answered has no key and the key keeps the deck's length`() {
        // Questions 0 to 3 answered, 1 and 3 wrong; 4 to 9 never reached.
        val key = keyAfterWrong(1, 3, nextSeq = 4)

        assertEquals(10, key.size)
        assertEquals(listOf(null, 1, null, 3) + List(6) { null }, key)
    }

    @Test
    fun `a second finish of the same race writes no second result`() {
        // Already finished when read.
        exists(race(finishedAt = System.currentTimeMillis()))
        val first = assertThrows(ResponseStatusException::class.java) { finish(190_000) }
        assertEquals(HttpStatus.CONFLICT, first.statusCode)

        // Two finishes at once: both read it open, only one update finishes it.
        exists(race())
        `when`(raceRepo.markFinished(anyValue(), ArgumentMatchers.anyLong())).thenReturn(null)
        val second = assertThrows(ResponseStatusException::class.java) { finish(190_000) }
        assertEquals("This race is already finished.", second.reason)

        verify(raceResultRepo, never()).save(anyValue<RaceResult>())
    }

    @Test
    fun `race settings of zero or below are refused`() {
        fun props(laps: Int = 3, lap: Double = 2641.0, speed: Double = 44.0, mul: Double = 1.7) =
            RaceProperties(laps = laps, lapLength = lap, topSpeed = speed, boostSeconds = 5.0, doubleMultiplier = mul, maxQuestions = 48)

        props()
        assertThrows(IllegalArgumentException::class.java) { props(laps = 0) }
        assertThrows(IllegalArgumentException::class.java) { props(lap = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { props(speed = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { props(mul = 0.0) }
    }
}
