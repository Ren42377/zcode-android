package com.zcode.android.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseTest {
    @Test
    fun extractsDataPayloads() {
        assertEquals("{\"x\":1}", Sse.dataPayload("data: {\"x\":1}"))
        assertNull(Sse.dataPayload(": ping"))
        assertNull(Sse.dataPayload("event: message"))
        assertNull(Sse.dataPayload(""))
        assertNull(Sse.dataPayload("data:"))
    }

    @Test
    fun detectsDoneMarkers() {
        assertTrue(Sse.isDone("data: [DONE]"))
        assertTrue(Sse.isDone("data:[DONE]"))
        assertFalse(Sse.isDone("data: {}"))
        assertFalse(Sse.isDone("event: done"))
    }
}
