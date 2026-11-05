package com.kanbanboard.mobile.di

import com.kanbanboard.mobile.core.data.demo.DemoState
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.StudyGroup
import java.time.Clock
import java.time.LocalDate

/** Версия mock: заполненная команда, проекты, задачи, события и файлы; пример пар без ИСУ и кэша. */
object InitialData {
    fun state(clock: Clock, files: FileStore): DemoState = MockData.create(clock, files::writeText)

    val exampleLessons: ((StudyGroup, LocalDate, LocalDate) -> List<Lesson>)? = MockData::lessons
}
