package com.immichframe.immichframe
import fi.iki.elonen.NanoHTTPD

class DimHttpServer(private val onDimCommand: (Boolean) -> Unit) : NanoHTTPD(8080) {

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/dim" -> {
                onDimCommand(true)
                newFixedLengthResponse("ImmichFrame dimmed")
            }
            "/undim" -> {
                onDimCommand(false)
                newFixedLengthResponse("ImmichFrame undimmed")
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unknown command")
        }
    }
}