package com.nofar.core.model

import java.time.Instant
import java.util.UUID

object ModelJson {
    fun coverageSetToJson(coverageSet: CoverageSet): String = buildString {
        append('{')
        append("\"id\":\"").append(coverageSet.id).append('"')
        append(",\"name\":\"").append(escape(coverageSet.name)).append('"')
        append(",\"downloadStatus\":\"").append(coverageSet.downloadStatus.name).append('"')
        append(",\"downloadProgressPct\":").append(coverageSet.downloadProgressPct)
        append(",\"entityCount\":").append(coverageSet.entityCount)
        append('}')
    }

    fun geoEntityToJson(entity: GeoEntity): String = buildString {
        append('{')
        append("\"id\":\"").append(escape(entity.id)).append('"')
        append(",\"osmType\":\"").append(entity.osmType.name.lowercase()).append('"')
        append(",\"name\":\"").append(escape(entity.name)).append('"')
        append(",\"type\":\"").append(entity.type.name.lowercase()).append('"')
        append(",\"lat\":").append(entity.lat)
        append(",\"lon\":").append(entity.lon)
        entity.elevation?.let { append(",\"elevation\":").append(it) }
        entity.elevationSource?.let {
            append(",\"elevationSource\":\"").append(it.name.lowercase()).append('"')
        }
        entity.footprintRadiusM?.let { append(",\"footprintRadiusM\":").append(it) }
        append('}')
    }

    fun demTileToJson(tile: DemTile): String = buildString {
        append('{')
        append("\"tileId\":\"").append(escape(tile.tileId)).append('"')
        append(",\"filePath\":\"").append(escape(tile.filePath)).append('"')
        append(",\"width\":").append(tile.width)
        append(",\"height\":").append(tile.height)
        append(",\"refCount\":").append(tile.refCount)
        append('}')
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun instantToEpochMillis(instant: Instant): Long = instant.toEpochMilli()

    fun epochMillisToInstant(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    fun uuidToString(uuid: UUID): String = uuid.toString()

    fun stringToUuid(value: String): UUID = UUID.fromString(value)
}
