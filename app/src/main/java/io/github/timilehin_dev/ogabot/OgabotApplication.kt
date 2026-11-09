package io.github.timilehin_dev.ogabot

import android.app.Application

class OgabotApplication : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.init()
    }
}
