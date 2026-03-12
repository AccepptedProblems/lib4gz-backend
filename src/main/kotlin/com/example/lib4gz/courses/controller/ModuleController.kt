package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.CreateModuleRequest
import com.example.lib4gz.courses.model.payload.ModuleResponse
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

    @GetMapping("/modules/{moduleId}")
    fun getModule(
        @PathVariable moduleId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ModuleResponse> {
        return moduleService.getModuleById(moduleId, userId)
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
