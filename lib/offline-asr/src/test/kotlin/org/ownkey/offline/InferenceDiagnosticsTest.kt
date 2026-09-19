package org.ownkey.offline

import kotlin.test.Test
import kotlin.test.assertEquals

class InferenceDiagnosticsTest {
    @Test
    fun neverIncludesNativeMessagesPathsOrCauses() {
        val sensitive = "/private/model.onnx; C:\\private\\model.onnx; dictated words"
        assertEquals("RUNTIME", InferenceDiagnostics.failureCode(IllegalStateException(sensitive, Exception(sensitive))))
        assertEquals("NATIVE_LIBRARY", InferenceDiagnostics.failureCode(UnsatisfiedLinkError(sensitive)))
        assertEquals("OUT_OF_MEMORY", InferenceDiagnostics.failureCode(OutOfMemoryError(sensitive)))
    }
}
