package dev.glowcow.altairgnss.ui.recording

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.recording.Activity
import dev.glowcow.altairgnss.ui.components.KindColors
import dev.glowcow.altairgnss.ui.theme.AltairIcons

internal val Activity.label: Int
    get() = when (this) {
        Activity.WALK -> R.string.activity_walk
        Activity.RUN -> R.string.activity_run
        Activity.HIKE -> R.string.activity_hike
        Activity.BIKE -> R.string.activity_bike
        Activity.SCOOTER -> R.string.activity_scooter
        Activity.MOTORCYCLE -> R.string.activity_motorcycle
        Activity.CAR -> R.string.activity_car
        Activity.OTHER -> R.string.activity_other
    }

internal val Activity.icon: ImageVector
    get() = when (this) {
        Activity.WALK -> AltairIcons.Walk
        Activity.RUN -> AltairIcons.Run
        Activity.HIKE -> AltairIcons.Hike
        Activity.BIKE -> AltairIcons.Bike
        Activity.SCOOTER -> AltairIcons.Scooter
        Activity.MOTORCYCLE -> AltairIcons.Motorcycle
        Activity.CAR -> AltairIcons.Car
        Activity.OTHER -> AltairIcons.Other
    }

internal val Activity.colour: Color
    get() = when (this) {
        Activity.WALK -> KindColors.Green
        Activity.RUN -> KindColors.Orange
        Activity.HIKE -> KindColors.Brown
        Activity.BIKE -> KindColors.Blue
        Activity.SCOOTER -> KindColors.Cyan
        Activity.MOTORCYCLE -> KindColors.Red
        Activity.CAR -> KindColors.DeepPurple
        // The palette's one grey: what is none of the others.
        Activity.OTHER -> KindColors.BlueGrey
    }
