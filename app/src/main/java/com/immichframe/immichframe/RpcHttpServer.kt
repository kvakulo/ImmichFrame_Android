package com.immichframe.immichframe

import fi.iki.elonen.NanoHTTPD

class RpcHttpServer(
    private val onDimCommand: (Boolean) -> Unit,
    private val onNextCommand: () -> Unit,
    private val onPreviousCommand: () -> Unit,
    private val onPauseCommand: () -> Unit,
    private val onSettingsCommand: () -> Unit
) : NanoHTTPD(53287) {

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/dim" -> {
                onDimCommand(true)
                newFixedLengthResponse("Dimmed")
            }
            "/undim" -> {
                onDimCommand(false)
                newFixedLengthResponse("Undimmed")
            }
            "/next" -> {
                onNextCommand()
                newFixedLengthResponse("Next")
            }
            "/previous" -> {
                onPreviousCommand()
                newFixedLengthResponse("Previous")
            }
            "/pause" -> {
                onPauseCommand()
                newFixedLengthResponse("Pause")
            }
            "/settings" -> {
                onSettingsCommand()
                newFixedLengthResponse("Settings")
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unknown command")
        }
    }
}
