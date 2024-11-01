package com.artranslate.ktorserver

import com.artranslate.ktorserver.transalate.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.websocket.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration


fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()  // 모듈 함수 호출
    }.start(wait = true)
}

fun Application.module() {

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE // Limit frame size for performance
        masking = false
    }

    routing {
        webSocket("/translate") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        val requestData: TranslationRequest = Json.decodeFromString(receivedText)
                        val responseData = translateText(requestData)
                        if (responseData != null){
                            val responseJson = Json.encodeToString(responseData)
                            println(responseJson)
                            send(responseJson)
                        } else {
                            send("{\"error\": \"Translation failed\"}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                environment?.log?.info("WebSocket connection closed")
                // 연결 종료 시 처리
            }
        }
    }
}