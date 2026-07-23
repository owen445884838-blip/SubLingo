package com.sublingo.app.data.db

import androidx.room.TypeConverter
import com.sublingo.app.domain.model.ProcessingStage
import com.sublingo.app.domain.model.ProcessingState

class AppTypeConverters {
    @TypeConverter
    fun fromProcessingStage(value: ProcessingStage): String = value.name

    @TypeConverter
    fun toProcessingStage(value: String): ProcessingStage = ProcessingStage.valueOf(value)

    @TypeConverter
    fun fromProcessingState(value: ProcessingState): String = value.name

    @TypeConverter
    fun toProcessingState(value: String): ProcessingState = ProcessingState.valueOf(value)
}
