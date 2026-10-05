package dev.glowcow.altairgnss

import android.app.Application
import android.content.Context
import dev.glowcow.altairgnss.airports.AirportService
import dev.glowcow.altairgnss.altimeter.Altimeter
import dev.glowcow.altairgnss.altimeter.PressureMonitor
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.gnss.GnssMonitor
import dev.glowcow.altairgnss.instruments.CompassMonitor
import dev.glowcow.altairgnss.update.AppUpdateScheduler
import dev.glowcow.altairgnss.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val gnss = GnssMonitor(context, scope)
    val compass = CompassMonitor(context)
    val altimeter = Altimeter(scope, gnss, PressureMonitor(context), settings)
    val appUpdater = AppUpdater(context, scope)
    val airports = AirportService("altair-gnss/${appUpdater.current}")
}

class AltairApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            container.settings.settings.map { it.appUpdate }.distinctUntilChanged()
                .collect { AppUpdateScheduler.apply(this@AltairApp, it && container.appUpdater.supported) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as AltairApp).container
