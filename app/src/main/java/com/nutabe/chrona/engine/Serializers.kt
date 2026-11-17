package com.nutabe.chrona.engine

import java.time.DayOfWeek
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object InstantAsEpochMillis : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantAsEpochMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())

    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

/** Stored as the ISO number (1 = Monday ... 7 = Sunday) so renaming never breaks a saved file. */
object DayOfWeekAsIsoNumber : KSerializer<DayOfWeek> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DayOfWeekAsIsoNumber", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeInt(value.value)

    override fun deserialize(decoder: Decoder): DayOfWeek = DayOfWeek.of(decoder.decodeInt())
}
