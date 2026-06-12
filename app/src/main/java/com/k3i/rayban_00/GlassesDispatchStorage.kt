package com.k3i.rayban_00

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val DISPATCH_PREFS = "glasses_dispatch_records"
private const val KEY_RECORDS = "records"
private const val RECORD_SEPARATOR = "\n"
private const val FIELD_SEPARATOR = "\t"
private const val MAX_STORED_DISPATCH_RECORDS = 12

fun loadGlassesDispatchRecords(context: Context): List<GlassesDispatchRecord> =
    context.getSharedPreferences(DISPATCH_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_RECORDS, null)
        ?.let(::deserializeGlassesDispatchRecords)
        .orEmpty()

fun saveGlassesDispatchRecords(context: Context, records: List<GlassesDispatchRecord>) {
    context.getSharedPreferences(DISPATCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_RECORDS, serializeGlassesDispatchRecords(records.takeLast(MAX_STORED_DISPATCH_RECORDS)))
        .apply()
}

fun serializeGlassesDispatchRecords(records: List<GlassesDispatchRecord>): String =
    records.joinToString(RECORD_SEPARATOR) { record ->
        listOf(
            record.sequence.toString(),
            record.route.name,
            record.accepted.toString(),
            encodeDispatchField(record.rendererName),
            record.availability.name,
            encodeDispatchField(record.documentId),
            encodeDispatchField(record.textKo),
            record.priority.name
        ).joinToString(FIELD_SEPARATOR)
    }

fun deserializeGlassesDispatchRecords(raw: String): List<GlassesDispatchRecord> =
    raw.lineSequence()
        .mapNotNull { line ->
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != 8) {
                null
            } else {
                runCatching {
                    GlassesDispatchRecord(
                        sequence = fields[0].toInt(),
                        route = GlassesDispatchRoute.valueOf(fields[1]),
                        accepted = fields[2].toBooleanStrict(),
                        rendererName = decodeDispatchField(fields[3]),
                        availability = GlassesRendererAvailability.valueOf(fields[4]),
                        documentId = decodeDispatchField(fields[5]),
                        textKo = decodeDispatchField(fields[6]),
                        priority = HudPriority.valueOf(fields[7])
                    )
                }.getOrNull()
            }
        }
        .toList()

private fun encodeDispatchField(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun decodeDispatchField(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
