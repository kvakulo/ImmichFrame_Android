package com.immichframe.immichframe

import fi.iki.elonen.NanoHTTPD

class RpcHttpServer(
    private val onDimCommand: (Boolean) -> Unit,
    private val onScreenOnCommand: () -> Unit,
    private val onScreenOffCommand: () -> Unit,
    private val onBrightnessCommand: (Float) -> Unit,
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
            "/screenoff" -> {
                onScreenOffCommand()
                newFixedLengthResponse("Screen Off")
            }
            "/screenon" -> {
                onScreenOnCommand()
                newFixedLengthResponse("Screen On")
            }
            "/brightness" -> {
                val brightness = session.parameters["value"]?.firstOrNull()?.toFloatOrNull()
                if (brightness != null && brightness >= -1 && brightness <= 1) {
                    onBrightnessCommand(brightness)
                    newFixedLengthResponse("Brightness set to $brightness")
                } else {
                    newFixedLengthResponse("Brightness parameter missing or invalid (should be a value between 0.00 and 1.00, or -1.00 to reset to default screen brightness)")
                }
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
