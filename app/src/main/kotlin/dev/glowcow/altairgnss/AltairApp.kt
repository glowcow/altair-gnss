package dev.glowcow.altairgnss

import android.app.Application
import android.content.Context
import dev.glowcow.altairgnss.airports.AirportService
import dev.glowcow.altairgnss.altimeter.Altimeter
import dev.glowcow.altairgnss.altimeter.PressureMonitor
import dev.glowcow.altairgnss.backup.Backup
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.gnss.FusedPosition
import dev.glowcow.altairgnss.gnss.GnssMonitor
import dev.glowcow.altairgnss.instruments.CompassMonitor
import dev.glowcow.altairgnss.maps.MapLoader
import dev.glowcow.altairgnss.maps.TileStore
import dev.glowcow.altairgnss.recording.AltairDatabase
import dev.glowcow.altairgnss.recording.Recorder
import dev.glowcow.altairgnss.update.AppUpdateScheduler
import dev.glowcow.altairgnss.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val gnss = GnssMonitor(context, scope, settings)
    val compass = CompassMonitor(context)
    val altimeter = Altimeter(scope, gnss, PressureMonitor(context), settings)
    val tracks = AltairDatabase.create(context).tracks()
    val recorder = Recorder(context, scope, tracks, altimeter, gnss, FusedPosition(context), settings)
    val backup = Backup(context, tracks, settings)
    val appUpdater = AppUpdater(context, scope)
    val airports = AirportService("altair-gnss/${appUpdater.current}")
    val tiles = TileStore(File(context.cacheDir, "tiles/osm"), "AltairGNSS/${appUpdater.current} (+https://github.com/glowcow/altair-gnss)")
    val maps = MapLoader(tiles)
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
