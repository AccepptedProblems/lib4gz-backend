package com.example.lib4gz.courses.model.payload

import com.example.lib4gz.courses.model.entity.FreshnessResourceType

/**
 * Response of `GET /v1/courses/{courseId}/last-updates`.
 *
 * Always carries all resource types; a type that has never been bumped is `0`.
 * `0` is a deterministic server answer (not a client default): clients cache data
 * against it and keep serving from cache until the first real mutation writes an
 * epoch-millis value, which forces a refetch everywhere at once.
 */
data class LastUpdatesResponse(
    val courseId: String,
    val lastUpdates: Map<FreshnessResourceType, Long>
)
