package app.mcleaner.core.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTasksTest {
    private val recents = """
        ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)
          * Recent #0: TaskRecord{ea62811 #2 I=com.android.launcher3/.Launcher U=0 StackId=0 sz=1}
            userId=0 effectiveUid=u0a22 mCallingUid=0 mUserSetupComplete=true mCallingPackage=null
            realActivity=com.android.launcher3/.Launcher
          * Recent #1: TaskRecord{16d9ce9 #58 A=com.shihab.diplay U=0 StackId=-1 sz=0}
            realActivity=com.shihab.diplay/com.shilapi.xcertplay.DiPlayActivity
          * Recent #2: TaskRecord{d161038 #57 A=app.mcleaner U=0 StackId=1 sz=1}
            realActivity=app.mcleaner/.MainActivity
          * Recent #3: TaskRecord{aaaaaaa #60 A=com.game U=0 StackId=5 sz=1}
            realActivity=com.game/.Main
    """.trimIndent()

    private val stacks = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
         configuration={1.0 [en_US] winConfig={ mActivityType=home} s.6}
          taskId=2: com.android.launcher3/com.android.launcher3.Launcher bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{com.android.launcher3/com.android.launcher3.Launcher}

        Stack id=5 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=60: com.game/com.game.Main bounds=[0,0][1920,720] userId=0 visible=false

        Stack id=6 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=61: com.game/com.game.Main bounds=[0,0][1920,720] userId=0 visible=false
          taskId=62: com.other/com.other.Main bounds=[0,0][1920,720] userId=0 visible=false
    """.trimIndent()

    @Test
    fun `recents dump gives task, package and stack`() {
        assertEquals(
            listOf(
                RecentTask(2, "com.android.launcher3", 0),
                RecentTask(58, "com.shihab.diplay", -1),
                RecentTask(57, "app.mcleaner", 1),
                RecentTask(60, "com.game", 5),
            ),
            RecentTasks.parseRecents(recents),
        )
    }

    @Test
    fun `stack list gives the tasks of each stack`() {
        assertEquals(
            listOf(
                ActivityStack(0, listOf(RecentTask(2, "com.android.launcher3", 0)), home = true),
                ActivityStack(5, listOf(RecentTask(60, "com.game", 5))),
                ActivityStack(6, listOf(RecentTask(61, "com.game", 6), RecentTask(62, "com.other", 6))),
            ),
            RecentTasks.parseStacks(stacks),
        )
    }

    @Test
    fun `cards left without a stack after force-stop are restored first`() {
        val tasks = RecentTasks.parseRecents(recents)
        assertEquals(listOf(58), RecentTasks.orphansToRestore(tasks, setOf("com.shihab.diplay", "com.game")))
    }

    @Test
    fun `only stacks made entirely of stopped apps are removed`() {
        val s = RecentTasks.parseStacks(stacks)
        assertEquals(listOf(5), RecentTasks.stacksToRemove(s, setOf("com.game")))
        assertEquals(listOf(5, 6), RecentTasks.stacksToRemove(s, setOf("com.game", "com.other")))
        assertEquals(emptyList<Int>(), RecentTasks.stacksToRemove(s, emptySet()))
    }

    @Test
    fun `home stack is never removed, even if the launcher was stopped`() {
        assertEquals(emptyList<Int>(), RecentTasks.stacksToRemove(RecentTasks.parseStacks(stacks), setOf("com.android.launcher3")))
    }

    @Test
    fun `binder path picks only the stopped packages`() {
        val tasks = listOf(RecentTask(5, "a.b"), RecentTask(6, "c.d"), RecentTask(7, "a.b"), RecentTask(8, null))
        assertEquals(listOf(5, 7), RecentTasks.idsToRemove(tasks, setOf("a.b")))
    }
}
