package com.nofar.core.visibility

interface VisibilityEngine {
    suspend fun computeVisibleEntities(request: VisibilityRequest): VisibilityResult
}
