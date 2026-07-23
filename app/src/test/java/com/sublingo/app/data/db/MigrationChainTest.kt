package com.sublingo.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationChainTest {
    @Test
    fun everySupportedSchemaFromTwoToCurrentHasAContinuousMigrationPath() {
        val ordered = ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertEquals((2..12).toList(), ordered.map { it.startVersion })
        assertEquals((3..13).toList(), ordered.map { it.endVersion })
        ordered.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endVersion, next.startVersion)
        }
        assertEquals(13, ordered.last().endVersion)
    }
}
