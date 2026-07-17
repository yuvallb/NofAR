package com.nofar.core.database.dao

import androidx.room.ColumnInfo

data class RegionEntityDisplayName(
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "display_name") val displayName: String
)
