package com.myfreax.webrtc

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.SendChannel

class Signaling(private val outgoing: SendChannel<Frame>, val connections: MutableSet<Connection?>) {

    companion object {
        private fun generateEventName(name: String): String {
            return "['${name}']"
        }

        suspend fun send(outgoing: SendChannel<Frame>, event: String, data: String) {
            outgoing.send(Frame.Text("${generateEventName(event)}$data"))
        }
    }

    fun getEventName(text:String):String?{
        return Regex("\\['(.+)'\\]").find(text)?.groups?.get(1)?.value
    }

    private suspend fun send(event: String, data: String) {
        outgoing.send(Frame.Text("${generateEventName(event)}$data"))
    }

    suspend fun ping(data: String) {
       send("pong","")
    }

    suspend fun message(message: String){
        // broadcast to clients
        connections.forEach { _ ->
            send("message",message)
        }
    }
}