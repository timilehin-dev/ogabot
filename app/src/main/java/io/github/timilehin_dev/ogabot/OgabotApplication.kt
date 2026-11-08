package io.github.timilehin_dev.ogabot

import android.app.Application

/**
 * Application entry point. The dependency graph is built here in later sprints;
 * for now it exists so the manifest has an application class to bind.
 */
class OgabotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
