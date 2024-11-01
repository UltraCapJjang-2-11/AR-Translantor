package com.artranslate.ktorserver.transalate

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

suspend fun translateText(requestData: TranslationRequest, retry: Int = 3): TranslationResponse?
{

    // CIO 클라이언트 생성
    // <나중에 HttpClient를 싱글톤 패턴으로 사용해서 최적화 필요>
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    // .env 파일에서 환경변수 값을 가져온다.
    val dotenv = dotenv()
    val apiKey = dotenv["GOOGLE_TRANSLATE_API_KEY"] ?: throw Exception("Can't find API Key")
    val apiUrl = dotenv["GOOGLE_TRANSLATE_API_URL"] ?: throw Exception("Can't find APU Url")

    repeat(retry)
    {
        try {
            // API 호출, HTTP POST
            val response: HttpResponse = client.post(apiUrl)
            {
                url {
                    parameters.append("key", apiKey)
                }
                contentType(ContentType.Application.Json)
                setBody(requestData)
            }


            // 응답 처리
            return if (response.status == HttpStatusCode.OK) {
                client.close()
                response.body()
            } else {
                println("Error: ${response.status}")
                client.close()
                null
            }

        } catch (e: Exception)
        {
            println("Request failed: $e. Retrying...")
            delay(1000L * (it + 1)) // 백오프
        }
    }

    println("Translation failed after $retry retries.")
    client.close()
    return null

}