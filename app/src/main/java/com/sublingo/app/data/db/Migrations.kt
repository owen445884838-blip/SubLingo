package com.sublingo.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val ALL_MIGRATIONS: Array<Migration> by lazy {
    arrayOf(
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
    )
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `LexemeEntity` (`id` TEXT NOT NULL, `lemma` TEXT NOT NULL, `normalizedLemma` TEXT NOT NULL, `language` TEXT NOT NULL, `phonetic` TEXT, `audioUrl` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_LexemeEntity_language_normalizedLemma` ON `LexemeEntity` (`language`, `normalizedLemma`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `LexemeSenseEntity` (`id` TEXT NOT NULL, `lexemeId` TEXT NOT NULL, `pos` TEXT, `definitionEn` TEXT NOT NULL, `definitionZh` TEXT, `source` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`lexemeId`) REFERENCES `LexemeEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_LexemeSenseEntity_lexemeId` ON `LexemeSenseEntity` (`lexemeId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `WordOccurrenceEntity` (`id` TEXT NOT NULL, `lexemeId` TEXT NOT NULL, `videoId` TEXT NOT NULL, `cueId` TEXT NOT NULL, `surfaceForm` TEXT NOT NULL, `contextEn` TEXT NOT NULL, `contextZh` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`lexemeId`) REFERENCES `LexemeEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`videoId`) REFERENCES `VideoEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`cueId`) REFERENCES `SubtitleCueEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_WordOccurrenceEntity_lexemeId_cueId_surfaceForm` ON `WordOccurrenceEntity` (`lexemeId`, `cueId`, `surfaceForm`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WordOccurrenceEntity_videoId` ON `WordOccurrenceEntity` (`videoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WordOccurrenceEntity_cueId` ON `WordOccurrenceEntity` (`cueId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ReviewCardEntity` (`id` TEXT NOT NULL, `lexemeId` TEXT NOT NULL, `repetitions` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, `easeFactor` REAL NOT NULL, `dueAt` INTEGER NOT NULL, `lastReviewedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`lexemeId`) REFERENCES `LexemeEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ReviewCardEntity_lexemeId` ON `ReviewCardEntity` (`lexemeId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `DictionaryCacheEntity` (`query` TEXT NOT NULL, `responseJson` TEXT, `state` TEXT NOT NULL, `errorMessage` TEXT, `expiresAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`query`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("WordOccurrenceEntity", "translationZh")) {
            db.execSQL("ALTER TABLE `WordOccurrenceEntity` ADD COLUMN `translationZh` TEXT")
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("WordOccurrenceEntity", "alignmentVersion")) {
            db.execSQL("ALTER TABLE `WordOccurrenceEntity` ADD COLUMN `alignmentVersion` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("VideoEntity", "vocabularyVersion")) {
            db.execSQL("ALTER TABLE `VideoEntity` ADD COLUMN `vocabularyVersion` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ReviewLogEntity` (`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `rating` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL, `previousRepetitions` INTEGER NOT NULL, `previousIntervalDays` INTEGER NOT NULL, `previousEaseFactor` REAL NOT NULL, `previousDueAt` INTEGER NOT NULL, `nextRepetitions` INTEGER NOT NULL, `nextIntervalDays` INTEGER NOT NULL, `nextEaseFactor` REAL NOT NULL, `nextDueAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`cardId`) REFERENCES `ReviewCardEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ReviewLogEntity_cardId` ON `ReviewLogEntity` (`cardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ReviewLogEntity_reviewedAt` ON `ReviewLogEntity` (`reviewedAt`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("WordOccurrenceEntity", "itemType")) {
            db.execSQL("ALTER TABLE `WordOccurrenceEntity` ADD COLUMN `itemType` TEXT NOT NULL DEFAULT 'WORD'")
        }
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `VocabularyLlmBatchEntity` (`id` TEXT NOT NULL, `videoId` TEXT NOT NULL, `version` INTEGER NOT NULL, `phase` TEXT NOT NULL, `inputHash` TEXT NOT NULL, `responseJson` TEXT, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `lastError` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`videoId`) REFERENCES `VideoEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_VocabularyLlmBatchEntity_videoId_version_phase_inputHash` ON `VocabularyLlmBatchEntity` (`videoId`, `version`, `phase`, `inputHash`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("WordOccurrenceEntity", "difficultyLevel")) {
            db.execSQL("ALTER TABLE `WordOccurrenceEntity` ADD COLUMN `difficultyLevel` TEXT NOT NULL DEFAULT 'UNKNOWN'")
        }
        if (!db.hasColumn("WordOccurrenceEntity", "difficultySource")) {
            db.execSQL("ALTER TABLE `WordOccurrenceEntity` ADD COLUMN `difficultySource` TEXT NOT NULL DEFAULT 'LOCAL'")
        }
        if (!db.hasColumn("WordOccurrenceEntity", "difficultyConfidence")) {
            db.execSQL("ALTER TABLE `WordOccurrenceEntity` ADD COLUMN `difficultyConfidence` REAL NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SubtitleWordAlignmentEntity` (`id` TEXT NOT NULL, `videoId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `englishSurface` TEXT NOT NULL, `chineseSurface` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`videoId`) REFERENCES `VideoEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_SubtitleWordAlignmentEntity_videoId` ON `SubtitleWordAlignmentEntity` (`videoId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_SubtitleWordAlignmentEntity_videoId_sequence_ordinal` ON `SubtitleWordAlignmentEntity` (`videoId`, `sequence`, `ordinal`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("SubtitleWordAlignmentEntity", "englishOccurrence")) {
            db.execSQL("ALTER TABLE `SubtitleWordAlignmentEntity` ADD COLUMN `englishOccurrence` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("ReviewCardEntity", "isFavorite")) {
            db.execSQL("ALTER TABLE `ReviewCardEntity` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        var found = false
        while (cursor.moveToNext() && !found) found = cursor.getString(nameIndex) == column
        found
    }
