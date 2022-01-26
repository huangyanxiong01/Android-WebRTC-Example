package com.myfreax.webrtc

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import java.time.*
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.reflect.full.callSuspend


fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        webSocket("/") { // websocketSession
            val thisConnection = Connection(this)
            connections += thisConnection
            println("You've connected in as [${thisConnection.name}]")
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        // Called when a new initiator is connected
                        val text = frame.readText()
                        val signaling = Signaling(outgoing,connections)
                        val eventName = signaling.getEventName(text)
                        val member = Signaling::class.members.find { it.name == eventName }
                        member?.callSuspend(signaling,eventName)
                        if (text.equals("bye", ignoreCase = true)) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                        }
                    }
                }
            }
        }
    }
}
