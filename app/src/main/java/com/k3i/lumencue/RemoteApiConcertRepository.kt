package com.k3i.lumencue

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MaxConcertApiResponseChars = 2_000_000

fun interface ConcertApiTransport {
    @Throws(IOException::class)
    fun get(path: String): String

    @Throws(IOException::class)
    fun post(path: String, body: String, bearerToken: String? = null): String =
        throw UnsupportedOperationException("POST를 지원하지 않는 transport입니다.")

    @Throws(IOException::class)
    fun delete(path: String, bearerToken: String): String =
        throw UnsupportedOperationException("DELETE를 지원하지 않는 transport입니다.")
}

class HttpConcertApiTransport(
    baseUrl: String,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 8_000
) : ConcertApiTransport {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/').also { value ->
        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("올바른 API URL이 필요합니다.", it) }
        require(uri.scheme == "https" || uri.scheme == "http") {
            "API URL은 http 또는 https여야 합니다."
        }
        require(!uri.host.isNullOrBlank()) { "API URL에 host가 필요합니다." }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "API 기본 URL에는 query 또는 fragment를 사용할 수 없습니다."
        }
    }

    override fun get(path: String): String = request("GET", path, null, null)

    override fun post(path: String, body: String, bearerToken: String?): String =
        request("POST", path, body, bearerToken)

    override fun delete(path: String, bearerToken: String): String =
        request("DELETE", path, null, bearerToken)

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearerToken: String?
    ): String {
        require(path.startsWith('/')) { "API path는 /로 시작해야 합니다." }
        val connection = URL("$normalizedBaseUrl$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            bearerToken?.takeIf { it.isNotBlank() }?.let { token ->
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            body?.let { payload ->
                val encoded = payload.toByteArray(StandardCharsets.UTF_8)
                require(encoded.size <= MaxConcertApiResponseChars) {
                    "API 요청이 허용 크기를 초과했습니다."
                }
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(encoded.size)
                connection.outputStream.use { it.write(encoded) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8_192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > MaxConcertApiResponseChars) {
                        throw IOException("API 응답이 허용 크기를 초과했습니다.")
                    }
                    result.append(buffer, 0, count)
                }
                result.toString()
            }.orEmpty()

            if (status !in 200..299) {
                val serverMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IOException(
                    buildString {
                        append("공연 API 요청 실패: HTTP ").append(status)
                        if (serverMessage.isNotBlank()) append(" (").append(serverMessage).append(')')
                    }
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}

class RemoteApiConcertRepository(
    private val transport: ConcertApiTransport
) : ConcertRepository {
    override fun loadConcertPackages(): ConcertRepositoryResult = runCatching {
        val listRoot = JSONObject(transport.get("/v1/concerts"))
        val descriptors = listRoot.requireDataObject()
            .getJSONArray("items")

        val events = mutableListOf<ConcertEvent>()
        val items = mutableListOf<ConcertPackageLoadItem>()
        val versions = mutableListOf<String>()

        for (index in 0 until descriptors.length()) {
            val descriptor = descriptors.getJSONObject(index)
            val eventId = descriptor.getString("id")
            val version = descriptor.getInt("packageVersion")
            val encodedEventId = URLEncoder.encode(eventId, StandardCharsets.UTF_8.name())
                .replace("+", "%20")

            runCatching {
                val packageData = JSONObject(
                    transport.get("/v1/concerts/$encodedEventId/package")
                ).requireDataObject()
                require(packageData.getString("eventId") == eventId) {
                    "목록과 패키지의 eventId가 일치하지 않습니다."
                }
                require(packageData.getInt("version") == version) {
                    "목록과 패키지의 version이 일치하지 않습니다."
                }

                val rawPackage = packageData.getJSONObject("package").toString()
                val validation = ConcertEventPackageParser.validate(rawPackage)
                val event = ConcertEventPackageParser.parse(rawPackage).copy(
                    emergencyNotice = packageData.optJSONObject("emergencyNotice")
                        ?.toEmergencyNotice()
                )
                require(event.id == eventId) {
                    "응답 경로와 패키지 본문의 eventId가 일치하지 않습니다."
                }
                Triple(event, validation.warnings, "$eventId:$version")
            }.fold(
                onSuccess = { (event, warnings, versionLabel) ->
                    events += event
                    versions += versionLabel
                    items += ConcertPackageLoadItem(
                        fileName = "remote:$versionLabel",
                        eventId = event.id,
                        eventTitle = event.title,
                        warnings = warnings
                    )
                },
                onFailure = { error ->
                    items += ConcertPackageLoadItem(
                        fileName = "remote:$eventId:$version",
                        eventId = eventId,
                        error = error.message ?: error::class.java.simpleName
                    )
                }
            )
        }

        val sourceVersion = versions.sorted().joinToString(",")
        val hasRejectedPackage = items.any { !it.loaded }
        ConcertRepositoryResult(
            source = ConcertRepositorySource.RemoteApi,
            packageReport = ConcertPackageLoadReport(
                events = events,
                items = items,
                fallbackUsed = false
            ),
            importValidationReport = validatePartnerImport(
                sourceName = "LumenCue Backend API",
                sourceVersion = sourceVersion,
                approvedVersion = sourceVersion.takeIf {
                    it.isNotBlank() && !hasRejectedPackage
                },
                providedFields = if (events.isNotEmpty()) {
                    defaultPartnerFieldMappings().map { it.sourceField }.toSet()
                } else {
                    emptySet()
                }
            ),
            message = if (hasRejectedPackage) {
                "원격 API에서 공연 ${events.size}개를 로드했고 ${items.count { !it.loaded }}개는 거부했습니다."
            } else {
                "원격 API에서 배포된 공연 ${events.size}개를 로드했습니다."
            }
        )
    }.getOrElse { error ->
        ConcertRepositoryResult(
            source = ConcertRepositorySource.RemoteApi,
            packageReport = ConcertPackageLoadReport(
                events = emptyList(),
                items = listOf(
                    ConcertPackageLoadItem(
                        fileName = "remote:/v1/concerts",
                        error = error.message ?: error::class.java.simpleName
                    )
                ),
                fallbackUsed = false
            ),
            importValidationReport = validatePartnerImport(
                sourceName = "LumenCue Backend API",
                sourceVersion = "",
                approvedVersion = null,
                providedFields = emptySet()
            ),
            message = "원격 공연 API를 사용할 수 없습니다: ${error.message ?: error::class.java.simpleName}"
        )
    }
}

private fun JSONObject.requireDataObject(): JSONObject =
    optJSONObject("data") ?: throw IllegalArgumentException("API 응답에 data object가 없습니다.")

private fun JSONObject.toEmergencyNotice(): EmergencyNotice =
    EmergencyNotice(
        id = getString("id"),
        severity = enumValueOf(getString("severity")),
        messageKo = getString("messageKo"),
        messageEn = getString("messageEn"),
        expiresAt = getString("expiresAt")
    )
