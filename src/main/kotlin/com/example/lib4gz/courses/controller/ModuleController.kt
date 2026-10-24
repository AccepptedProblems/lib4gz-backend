package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.courses.model.payload.CreateModuleRequest
import com.example.lib4gz.courses.model.payload.ModuleResponse
import com.example.lib4gz.courses.model.payload.ReorderModulesRequest
import com.example.lib4gz.courses.model.payload.UpdateModuleRequest
import com.example.lib4gz.courses.service.ModuleService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class ModuleController(
    private val moduleService: ModuleService
) {

    @PostMapping("/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    fun createModule(
        @PathVariable courseId: String,
        @RequestBody createRequest: CreateModuleRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ModuleResponse> {
        return moduleService.createModule(courseId, userId, createRequest)
    }

    @GetMapping("/courses/{courseId}/modules")
    fun listCourseModules(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<ModuleResponse> {
        return moduleService.listCourseModules(courseId, userId)
    }

    /**
     * Rewrites the order of a course's modules. The body lists every visible module once, in the
     * new order; anything else is a 400. Returns the modules as the list endpoint does.
     */
    @PutMapping("/courses/{courseId}/modules/order")
    fun reorderModules(
        @PathVariable courseId: String,
        @RequestBody request: ReorderModulesRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<ModuleResponse> {
        return moduleService.reorderModules(courseId, userId, request.moduleIds)
    }

    /** `?expand=submissionCount` adds, for a teacher, how many student submissions the module holds. */
    @GetMapping("/modules/{moduleId}")
    fun getModule(
        @PathVariable moduleId: String,
        @RequestParam(required = false) expand: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ModuleResponse> {
        return moduleService.getModuleById(moduleId, userId, ExpandTokens.parse(expand))
    }

    @PatchMapping("/modules/{moduleId}")
    fun updateModule(
        @PathVariable moduleId: String,
        @RequestBody updateRequest: UpdateModuleRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ModuleResponse> {
        return moduleService.updateModule(moduleId, userId, updateRequest)
    }

    @DeleteMapping("/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteModule(
        @PathVariable moduleId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return moduleService.deleteModule(moduleId, userId)
    }
}
