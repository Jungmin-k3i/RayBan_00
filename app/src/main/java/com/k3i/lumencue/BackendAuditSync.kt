package com.k3i.lumencue

import org.json.JSONObject

data class BackendAudienceSession(
    val eventId: String,
    val sessionId: String,
    val accessToken: String,
    val expiresAt: String
)

data class DispatchAuditSyncReport(
    val attemptedCount: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val lastError: String? = null
)

data class AudienceDataDeletionResult(
    val receiptId: String,
    val scope: String,
    val ticketsDeleted: Int,
    val sessionsDeleted: Int,
    val dispatchLogsDeleted: Int,
    val completedAt: String
)

class BackendAuditClient(
    private val transport: ConcertApiTransport
) {
    fun verifyTicket(eventId: String, ticket: ConcertTicket): BackendAudienceSession {
        require(ticket.provider.isNotBlank()) { "티켓 provider가 필요합니다." }
        require(ticket.ticketId.isNotBlank()) { "ticketId가 필요합니다." }
        val request = JSONObject()
            .put("provider", ticket.provider)
            .put("ticketId", ticket.ticketId)
            .put("eventId", eventId)
        val data = JSONObject(
            transport.post("/v1/tickets/verify", request.toString())
        ).requireAuditDataObject()
        require(data.optBoolean("authorized")) { "백엔드가 티켓을 승인하지 않았습니다." }
        require(data.getString("eventId") == eventId) {
            "티켓 세션과 공연이 일치하지 않습니다."
        }
        return BackendAudienceSession(
            eventId = eventId,
            sessionId = data.getString("sessionId"),
            accessToken = data.getString("accessToken"),
            expiresAt = data.getString("expiresAt")
        )
    }

    fun syncDispatchRecords(
        session: BackendAudienceSession,
        records: List<GlassesDispatchRecord>
    ): DispatchAuditSyncReport {
        val candidates = records.filter { it.eventId == session.eventId }
        var synced = 0
        var lastError: String? = null
        candidates.forEach { record ->
            runCatching {
                val request = JSONObject()
                    .put("eventId", session.eventId)
                    .put("clientRecordId", record.clientRecordId)
                    .put("route", record.route.name)
                    .put("accepted", record.accepted)
                    .put("rendererName", record.rendererName)
                    .put("availability", record.availability.name)
                    .put("documentId", record.documentId)
                    .put("priority", record.priority.name)
                    .put(
                        "metadata",
                        JSONObject()
                            .put("sequence", record.sequence)
                            .put("textKo", record.textKo.take(500))
                    )
                transport.post(
                    path = "/v1/device-dispatch-logs",
                    body = request.toString(),
                    bearerToken = session.accessToken
                )
            }.onSuccess {
                synced += 1
            }.onFailure { error ->
                lastError = error.message ?: error::class.java.simpleName
            }
        }
        return DispatchAuditSyncReport(
            attemptedCount = candidates.size,
            syncedCount = synced,
            failedCount = candidates.size - synced,
            lastError = lastError
        )
    }

    fun deleteAudienceData(session: BackendAudienceSession): AudienceDataDeletionResult {
        val data = JSONObject(
            transport.delete("/v1/me/data", session.accessToken)
        ).requireAuditDataObject()
        return AudienceDataDeletionResult(
            receiptId = data.getString("receiptId"),
            scope = data.getString("scope"),
            ticketsDeleted = data.getInt("ticketsDeleted"),
            sessionsDeleted = data.getInt("sessionsDeleted"),
            dispatchLogsDeleted = data.getInt("dispatchLogsDeleted"),
            completedAt = data.getString("completedAt")
        )
    }
}

private fun JSONObject.requireAuditDataObject(): JSONObject =
    optJSONObject("data") ?: throw IllegalArgumentException("API 응답에 data object가 없습니다.")
