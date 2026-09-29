package com.k3i.lumencue

/** Keep lens content brief; scene/accessibility metadata is not a second visible caption. */
fun ToolkitDisplayDocument.physicalDisplayLines(): List<String> = elements
    .filter { it.type == ToolkitDisplayElementType.Text && it.metadata["accessibility"] != "true" }
    .map { it.value.trim().take(160) }
    .filter { it.isNotBlank() }
    .distinct()
    .take(2)
    .ifEmpty { listOf("LumenCue") }
