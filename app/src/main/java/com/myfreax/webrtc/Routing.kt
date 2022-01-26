package com.myfreax.webrtc

import android.content.Context
import io.ktor.routing.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

fun Application.configureRouting(context: Context) {
    routing {
        get("/hello") {
            call.respondText(text = "Hello World", contentType = ContentType.Text.Html)
        }

        get("/") {
            call.respondOutputStream(
                contentType = ContentType.Text.Html,
                HttpStatusCode.OK
            ) {
                context.assets.open("dist/index.html").copyTo(this)
            }
        }
    }
}
