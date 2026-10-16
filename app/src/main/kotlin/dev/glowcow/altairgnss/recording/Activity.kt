package dev.glowcow.altairgnss.recording

/** What a recording is made doing; stored by [key], so the order and the names may change. */
enum class Activity(val key: String) {
    WALK("walk"),
    RUN("run"),
    HIKE("hike"),
    BIKE("bike"),
    SCOOTER("scooter"),
    MOTORCYCLE("motorcycle"),
    CAR("car"),
    OTHER("other"),
    ;

    companion object {
        fun of(key: String?): Activity? = entries.firstOrNull { it.key == key }
    }
}
