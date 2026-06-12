package com.k3i.rayban_00

import android.content.Context

private const val ConcertPackageAssetDir = "concert_packages"

data class ConcertPackageLoadItem(
    val fileName: String,
    val eventId: String? = null,
    val eventTitle: String? = null,
    val error: String? = null,
    val warnings: List<String> = emptyList()
) {
    val loaded: Boolean
        get() = error == null
}

data class ConcertPackageLoadReport(
    val events: List<ConcertEvent>,
    val items: List<ConcertPackageLoadItem>,
    val fallbackUsed: Boolean
) {
    val loadedCount: Int
        get() = items.count { it.loaded }

    val failedCount: Int
        get() = items.count { !it.loaded }

    val warningCount: Int
        get() = items.sumOf { it.warnings.size }
}

fun loadConcertEventsFromAssets(context: Context): List<ConcertEvent> =
    loadConcertPackageReportFromAssets(context).events

fun loadConcertPackageReportFromAssets(context: Context): ConcertPackageLoadReport =
    runCatching {
        val assetManager = context.assets
        val packageFiles = assetManager.list(ConcertPackageAssetDir)
            ?.filter { it.endsWith(".json") }
            .orEmpty()

        val loadedEvents = mutableListOf<ConcertEvent>()
        val items = packageFiles.map { fileName ->
            runCatching {
                assetManager.open("$ConcertPackageAssetDir/$fileName").bufferedReader().use { reader ->
                    val rawPackage = reader.readText()
                    val validation = ConcertEventPackageParser.validate(rawPackage)
                    val event = ConcertEventPackageParser.parse(rawPackage)
                    event to validation.warnings
                }
            }.fold(
                onSuccess = { (event, warnings) ->
                    loadedEvents += event
                    ConcertPackageLoadItem(
                        fileName = fileName,
                        eventId = event.id,
                        eventTitle = event.title,
                        warnings = warnings
                    )
                },
                onFailure = { error ->
                    ConcertPackageLoadItem(
                        fileName = fileName,
                        error = error.message ?: error::class.java.simpleName
                    )
                }
            )
        }

        ConcertPackageLoadReport(
            events = loadedEvents,
            items = items,
            fallbackUsed = loadedEvents.isEmpty()
        )
    }.getOrElse { error ->
        ConcertPackageLoadReport(
            events = emptyList(),
            items = listOf(
                ConcertPackageLoadItem(
                    fileName = ConcertPackageAssetDir,
                    error = error.message ?: error::class.java.simpleName
                )
            ),
            fallbackUsed = true
        )
    }

fun fallbackConcertPackageLoadReport(events: List<ConcertEvent>): ConcertPackageLoadReport =
    ConcertPackageLoadReport(
        events = events,
        items = events.map { event ->
            ConcertPackageLoadItem(
                fileName = "fallback:${event.id}",
                eventId = event.id,
                eventTitle = event.title
            )
        },
        fallbackUsed = true
    )
