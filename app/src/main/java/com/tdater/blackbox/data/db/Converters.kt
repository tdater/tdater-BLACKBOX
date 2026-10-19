package com.tdater.blackbox.data.db

import androidx.room.TypeConverter
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.PlanSnapshot
import com.tdater.blackbox.core.model.RunMarker
import com.tdater.blackbox.core.model.SourceRef
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Small ordered lists and frozen snapshots are stored as JSON columns. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val strings = ListSerializer(String.serializer())
    private val measurements = ListSerializer(Measurement.serializer())
    private val markers = ListSerializer(RunMarker.serializer())
    private val refs = ListSerializer(SourceRef.serializer())

    @TypeConverter fun fromStrings(v: List<String>): String = json.encodeToString(strings, v)
    @TypeConverter fun toStrings(v: String): List<String> = json.decodeFromString(strings, v)

    @TypeConverter fun fromMeasurements(v: List<Measurement>): String = json.encodeToString(measurements, v)
    @TypeConverter fun toMeasurements(v: String): List<Measurement> = json.decodeFromString(measurements, v)

    @TypeConverter fun fromMarkers(v: List<RunMarker>): String = json.encodeToString(markers, v)
    @TypeConverter fun toMarkers(v: String): List<RunMarker> = json.decodeFromString(markers, v)

    @TypeConverter fun fromRefs(v: List<SourceRef>): String = json.encodeToString(refs, v)
    @TypeConverter fun toRefs(v: String): List<SourceRef> = json.decodeFromString(refs, v)

    @TypeConverter fun fromSnapshot(v: PlanSnapshot): String = json.encodeToString(PlanSnapshot.serializer(), v)
    @TypeConverter fun toSnapshot(v: String): PlanSnapshot = json.decodeFromString(PlanSnapshot.serializer(), v)
}
