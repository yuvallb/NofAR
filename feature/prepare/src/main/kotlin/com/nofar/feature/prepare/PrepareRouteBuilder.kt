package com.nofar.feature.prepare

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

const val PREPARE_ROUTE = "prepare"
const val PREPARE_ROUTE_WITH_ARG =
    "prepare?coverageSetId={coverageSetId}&centerLat={centerLat}&centerLon={centerLon}&radiusM={radiusM}&name={name}"

data class PrepareNavArgs(
    val coverageSetId: UUID? = null,
    val centerLat: Double? = null,
    val centerLon: Double? = null,
    val radiusM: Double? = null,
    val name: String? = null
)

object PrepareRouteBuilder {
    fun build(
        coverageSetId: UUID? = null,
        centerLat: Double? = null,
        centerLon: Double? = null,
        radiusM: Double? = null,
        name: String? = null
    ): String {
        val encodedName =
            name?.takeIf { it.isNotBlank() }
                ?.let { URLEncoder.encode(it, UTF_8) }
                .orEmpty()
        return buildString {
            append("prepare?coverageSetId=")
            append(coverageSetId?.toString().orEmpty())
            append("&centerLat=")
            append(centerLat?.toString().orEmpty())
            append("&centerLon=")
            append(centerLon?.toString().orEmpty())
            append("&radiusM=")
            append(radiusM?.toString().orEmpty())
            append("&name=")
            append(encodedName)
        }
    }

    fun build(args: PrepareNavArgs): String = build(
        coverageSetId = args.coverageSetId,
        centerLat = args.centerLat,
        centerLon = args.centerLon,
        radiusM = args.radiusM,
        name = args.name
    )

    fun parseDouble(raw: String?): Double? = raw?.takeIf { it.isNotBlank() }?.toDoubleOrNull()

    fun parseName(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.let { URLDecoder.decode(it, UTF_8) }

    private const val UTF_8 = "UTF-8"
}
