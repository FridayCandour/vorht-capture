package com.vorht.capture

import com.vorht.capture.capture.CodeParser
import com.vorht.capture.net.CarlaDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTests {

    @Test
    fun testCodeParserDashed() {
        val code = CodeParser.parse("Your WhatsApp code: 482-913. Do not share this code.")
        assertEquals("482913", code)
    }

    @Test
    fun testCodeParserKeywordAndDigits() {
        val code = CodeParser.parse("Your verification code is 123456")
        assertEquals("123456", code)
    }

    @Test
    fun testCodeParserAlnum() {
        val code = CodeParser.parse("Use BIRTH123 to verify your account")
        assertEquals("BIRTH123", code)
    }

    @Test
    fun testCodeParserOtp() {
        val code = CodeParser.parse("OTP: 987654")
        assertEquals("987654", code)
    }

    @Test
    fun testCarlaMessageFormatting() {
        val formatted = CarlaDelivery.formatMessage(
            sender = "John Doe",
            text = "Your verification code is BIRTH123",
        )
        assertNotNull(formatted)
        assertTrue(formatted.contains("From: John Doe"))
        assertTrue(formatted.contains("Message: Your verification code is BIRTH123"))
        org.junit.Assert.assertFalse(formatted.contains("VORHT CAPTURE"))
        org.junit.Assert.assertFalse(formatted.contains("Code:"))
        org.junit.Assert.assertFalse(formatted.contains("At:"))
        org.junit.Assert.assertFalse(formatted.contains("Event:"))
    }
}
