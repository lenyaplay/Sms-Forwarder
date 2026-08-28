package com.smsforwarder.gateway.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class WebhookUrlBuilderTest {

    @Test
    fun `appends webhook path and query param to a server url without trailing slash`() {
        assertEquals(
            "http://127.0.0.1:8080/webhook?upload_token=abc",
            WebhookUrlBuilder.build("http://127.0.0.1:8080", "abc"),
        )
    }

    @Test
    fun `does not double the slash when server url already ends with one`() {
        assertEquals(
            "http://127.0.0.1:8080/webhook?upload_token=abc",
            WebhookUrlBuilder.build("http://127.0.0.1:8080/", "abc"),
        )
    }
}
