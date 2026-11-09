package com.kanbanboard.mobile.di

import com.kanbanboard.mobile.core.data.demo.DemoSeed
import com.kanbanboard.mobile.core.data.demo.DemoState
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.StudyGroup
import java.time.Clock
import java.time.LocalDate

/** Версия clean: чистый старт — только тимлид и участник, без примеров пар. */
object InitialData {
    @Suppress("UNUSED_PARAMETER")
    fun state(clock: Clock, files: FileStore): DemoState = DemoSeed.create(clock)

    val exampleLessons: ((StudyGroup, LocalDate, LocalDate) -> List<Lesson>)? = null
}
