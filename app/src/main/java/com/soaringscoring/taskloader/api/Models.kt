package com.soaringscoring.taskloader.api

import kotlinx.serialization.Serializable

@Serializable
data class Contest(
    val id: String,
    val slug: String? = null,
    val name: String,
    val organisationName: String? = null,
    val startDate: String,
    val endDate: String,
    val timezone: String? = null
)

@Serializable
data class ContestsResponse(val contests: List<Contest>)

@Serializable
data class ContestClass(
    val id: String,
    val name: String,
    val code: String? = null,
    val isVirtual: Boolean? = null,
    val parentClassId: String? = null
)

@Serializable
data class ClassesResponse(val classes: List<ContestClass>)

@Serializable
data class TaskFiles(
    val seeyouCup: String,
    val xcsoarTsk: String,
    val xctsk: String
)

@Serializable
data class TaskRow(
    val dayId: String,
    val taskId: String,
    val dayNumber: Int,
    val date: String,
    val classId: String? = null,
    val className: String? = null,
    val displayLabel: String,
    val isOfficialTask: Boolean,
    val files: TaskFiles,
    val dhtHandicap: Double? = null,
    val dhtDistanceKm: Double? = null
)

@Serializable
data class TasksContestSummary(
    val id: String,
    val slug: String? = null,
    val name: String
)

@Serializable
data class TasksResponse(
    val contest: TasksContestSummary,
    val tasks: List<TaskRow>
)

/** Covers both observed error shapes: {"error":"msg"} and {"error":{"code":"X","message":"msg"}} */
@Serializable
data class ApiErrorEnvelope(
    val error: kotlinx.serialization.json.JsonElement? = null
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String, val httpCode: Int? = null, val code: String? = null) : ApiResult<Nothing>()
}
