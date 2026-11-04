package com.kanbanboard.mobile

import android.app.Application
import com.kanbanboard.mobile.di.AppContainer

class KanbanApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
