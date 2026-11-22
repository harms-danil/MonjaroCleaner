package app.mcleaner.privileged

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellRunnerTest {
    @Test
    fun returnsExitCodeAndOutput() {
        assertEquals(3 to "hi\n", ShellRunner.run("echo hi; exit 3", timeoutMs = 5_000, maxChars = 1_000))
    }

    @Test
    fun hangingCommand_timesOut() {
        val start = System.currentTimeMillis()
        val (code, _) = ShellRunner.run("sleep 20", timeoutMs = 500, maxChars = 1_000)
        assertEquals(ShellRunner.TIMEOUT, code)
        assertTrue("took ${System.currentTimeMillis() - start} ms", System.currentTimeMillis() - start < 5_000)
    }

    @Test
    fun hugeOutput_rejectedNotTruncated() {
        val (code, out) = ShellRunner.run("yes x | head -c 50000", timeoutMs = 5_000, maxChars = 1_000)
        assertEquals(ShellRunner.TOO_LARGE, code)
        assertTrue(out.length < 1_000)
    }
}
