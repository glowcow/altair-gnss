# kotlinx.serialization: keep generated serializers of @Serializable classes (nav keys).
-keepclassmembers @kotlinx.serialization.Serializable class dev.glowcow.altairgnss.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
