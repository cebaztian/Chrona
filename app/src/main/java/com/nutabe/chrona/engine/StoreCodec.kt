package com.nutabe.chrona.engine

import kotlinx.serialization.json.Json

/** Pure JSON codec for the store file, kept free of Android types so it is unit tested on the JVM. */
object StoreCodec {
    sealed interface DecodeResult {
        data class Ok(val data: StoreData) : DecodeResult
        data class Corrupt(val cause: Throwable) : DecodeResult
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(data: StoreData): String = json.encodeToString(StoreData.serializer(), data)

    fun decode(text: String): DecodeResult =
        runCatching { json.decodeFromString(StoreData.serializer(), text) }
            .fold({ DecodeResult.Ok(it) }, { DecodeResult.Corrupt(it) })
}
