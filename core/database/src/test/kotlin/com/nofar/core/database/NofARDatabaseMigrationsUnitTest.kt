package com.nofar.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NofARDatabaseMigrationsUnitTest {
    @Test
    fun migrations_coverVersionsOneThroughFour() {
        assertThat(NofARDatabaseMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(NofARDatabaseMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
        assertThat(NofARDatabaseMigrations.MIGRATION_2_3.startVersion).isEqualTo(2)
        assertThat(NofARDatabaseMigrations.MIGRATION_2_3.endVersion).isEqualTo(3)
        assertThat(NofARDatabaseMigrations.MIGRATION_3_4.startVersion).isEqualTo(3)
        assertThat(NofARDatabaseMigrations.MIGRATION_3_4.endVersion).isEqualTo(4)
    }
}
