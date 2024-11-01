package com.artranslate.ktorclient

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.HttpMethod
import io.ktor.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TranslationRequest(
    val q: List<String>,
    val target: String,
    val source: String? = null,
    val format: String? = "text",
    val model: String? = null
)

@Serializable
data class TranslationResponse(
    val data: TranslationsData
)

@Serializable
data class TranslationsData(
    val translations: List<Translation>
)

@Serializable
data class Translation(
    val translatedText: String
)


suspend fun main() = coroutineScope {
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    client.webSocket(method = HttpMethod.Get, host = "localhost", port = 8080, path = "/translate") {
        println("서버에 연결되었습니다.")

        while (true) {
            val texts = mutableListOf<String>()
            println("번역할 텍스트를 입력하세요 (종료하려면 빈 줄 입력):")

            while (true) {
                val userInput = readlnOrNull()?.takeIf { it.isNotBlank() } ?: break
                texts.add(userInput)
            }

            if (texts.isEmpty()) break

            val requestData = TranslationRequest(q = texts, target = "ko")
            val requestJson = Json.encodeToString(requestData)
            send(requestJson)

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val responseText = frame.readText()
                    val responseData: TranslationResponse = Json.decodeFromString(responseText)
                    responseData.data.translations.forEach {
                        println("번역된 텍스트: ${it.translatedText}")
                    }
                    break
                }
            }
        }
    }

    client.close()
}