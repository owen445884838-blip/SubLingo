package com.sublingo.app.di

import com.sublingo.app.data.task.FakeTaskPlannerProvider
import com.sublingo.app.data.task.YoutubeDlTaskFlowProvider
import com.sublingo.app.domain.provider.TaskFlowProvider
import com.sublingo.app.domain.provider.TaskPlannerProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskBindings {
    @Binds abstract fun bindTaskFlowProvider(impl: YoutubeDlTaskFlowProvider): TaskFlowProvider
    @Binds abstract fun bindTaskPlannerProvider(impl: FakeTaskPlannerProvider): TaskPlannerProvider
}
