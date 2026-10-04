package org.openmobifitness.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*
import java.io.File
import java.time.*
import java.util.UUID

/** Dedicated emulator only. Fixtures are explicit; screenshots are native Compose rendering. */
class V030UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=(compose.activity.application as OpenMobiApp).controller
    private val args get()=InstrumentationRegistry.getArguments()
    private val layout get()=args.getString("case","phone")
    private val addedName get()="Alexandra "+layout.take(10)
    private val a=UUID.nameUUIDFromBytes("v030-ui-a".toByteArray()).toString()
    private val b=UUID.nameUUIDFromBytes("v030-ui-b".toByteArray()).toString()
    private fun id(machine: Machine)=UUID.nameUUIDFromBytes("v030-ui-${machine.name}".toByteArray()).toString()
    private fun SemanticsNodeInteraction.reveal(): SemanticsNodeInteraction = apply { if(runCatching { assertIsDisplayed() }.isFailure) performScrollTo() }
    private fun text(res: Int)=compose.activity.getString(res)
    private fun page(index: Int) { compose.runOnUiThread { c.userPicker.value=false; c.cancelStart(); c.dismissResult(); compose.activity.focusTraining.value=false; compose.activity.page.value=index; compose.activity.settingsSection.value="" }; compose.waitForIdle() }
    private fun openFixture(machine: Machine) {
        // Earlier emulator runs may have created more than one page of newer sessions.
        repeat(12) {
            if(runCatching { compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("record-${id(machine)}")) }.isSuccess) {
                compose.onNodeWithTag("record-${id(machine)}").performClick(); return
            }
            compose.onNodeWithContentDescription(text(R.string.next_page)).reveal().assertIsEnabled().performClick()
            compose.waitForIdle()
            compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }
        }
        error("Fixture was not found in history")
    }
    private fun screen(name: String) {
        compose.waitForIdle(); Thread.sleep(250)
        val device=UiDevice.getInstance(inst)
        if(device.hasObject(By.textContains("System UI")) && device.hasObject(By.res("android:id/aerr_wait"))) { device.findObject(By.res("android:id/aerr_wait")).click(); Thread.sleep(1500) }
        assertFalse("System error obscures screenshot",device.hasObject(By.res("android:id/aerr_wait")))
        val picture=inst.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val dir=File(compose.activity.getExternalFilesDir(null),"v030-screens/$layout").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { picture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; picture.recycle()
        val cfg=compose.activity.resources.configuration
        File(dir,"environment.txt").writeText("${cfg.screenWidthDp}x${cfg.screenHeightDp}dp font=${cfg.fontScale} locale=${cfg.locales.toLanguageTags()} theme=${c.theme.value}\nSynthetic UI data; no GATT\n")
    }
    @Before fun seed() {
        Configurator.getInstance().waitForIdleTimeout=100
        listOf(android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.POST_NOTIFICATIONS).forEach { runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,it) } }
        compose.waitUntil(90000) { c.state.value.ready }
        compose.runOnUiThread { c.cancelStart(); c.userPicker.value=false; c.dismissResult() }
        runBlocking {
            if(c.state.value.session!=null) c.finish().join()
            c.repo.users.value.firstOrNull { it.name==addedName }?.let { c.repo.removeUser(c.repo.removalPreview(it.id),true,true) }
            c.repo.saveUser(UserProfile(a,"小林",weightKg=65.0)); c.repo.saveUser(UserProfile(b,"小夏"))
            c.switchUser(a).join()
            Machine.entries.filter { it !in setOf(Machine.UNKNOWN,Machine.HEART) }.forEachIndexed { index,machine ->
                c.repo.deleteSession(id(machine))
                val start=LocalDate.now().atTime(8,index).atZone(ZoneId.systemDefault()).toInstant()
                val session=Session(id=id(machine),ownerUserId=a,startedUserId=a,startedUserName="小林",identityVersion=1,start=start.toString(),end=start.plusSeconds(1200).toString(),device="UI fixture v0.3.0",machine=machine,protocol=Protocol.DEMO,demo=true,status="completed",elapsedMs=1200000,caloriesKcal=160.0,distanceM=if(index<4) 3200.0 else null,weightKg=65.0,met=5.0,weightSource="user",metSource="default")
                val samples=(0..240).map { i -> Sample(session.id,i*5000L,Metrics(heartBpm=if(i in 80..90) null else 122+i%20,cadence=if(machine!=Machine.TREADMILL && machine!=Machine.DUMBBELL) 50.0+i%12 else null,resistance=if(index<2) 12.0 else null,speedMps=if(index<4) 2.6 else null,distanceM=session.distanceM?.times(i/240.0),powerW=if(index<3) 130.0+i%12 else null,strokes=if(machine==Machine.ROWER) i*3 else null,stepRate=if(machine==Machine.TREADMILL) 140.0+i%20 else null,stepCount=if(machine==Machine.TREADMILL) i*12 else null,inclinePercent=if(machine==Machine.TREADMILL) 2.0 else null,strideM=if(machine==Machine.TREADMILL) .78 else null,forceN=if(machine==Machine.ROWER) 180.0 else null,jumpCount=if(machine==Machine.JUMP_ROPE) i*5 else null,continuousJumps=if(machine==Machine.JUMP_ROPE) i.coerceAtMost(145) else null,jumpInterruptions=if(machine==Machine.JUMP_ROPE) i/50 else null,repetitions=if(machine==Machine.DUMBBELL) i/6 else null,loadKg=if(machine==Machine.DUMBBELL) 6.0 else null)) }
                withContext(Dispatchers.IO) {
                    c.repo.db.runInTransaction { c.repo.db.records().session(session.row()); c.repo.db.records().samples(samples.map(Sample::row)) }
                    c.repo.refresh()
                }
            }
        }
        compose.runOnUiThread { c.setDemo(true); c.setTheme(if(layout.contains("dark")) "dark" else "light"); c.historyScope.value="current"; c.setIdentityPolicy(IdentityPolicy.EACH_RECORDING); c.error(null) }
        page(0)
    }
    @After fun cleanup() { compose.runOnUiThread { c.cancelStart(); c.userPicker.value=false; c.dismissResult() }; runBlocking { if(c.state.value.session!=null) c.finish().join() } }
    @Test fun responsivePagesAndTypedPlanDraft() {
        compose.onNodeWithTag("free-training-card").assertExists(); screen("01-home")
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-warmup_elliptical"))
        screen("02-plan-grid")
        if(args.getString("expectedColumns")!=null) {
            val expected=args.getString("expectedColumns")!!.toInt()
            val ids=listOf("warmup","recovery","steady20").take(expected)
            val positions=ids.map { compose.onNodeWithTag("plan-${it}_elliptical").fetchSemanticsNode().boundsInRoot }
            assertTrue(positions.all { kotlin.math.abs(it.top-positions.first().top)<2f })
            assertEquals(expected,positions.map { it.left }.distinct().size)
        }
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("new-plan"))
        compose.onNodeWithTag("new-plan").performClick()
        compose.onNodeWithTag("plan-title").performTextReplacement("午后间歇")
        compose.onNodeWithTag("plan-title").performImeAction(); screen("03-editor")
        compose.onNodeWithTag("add-stage").reveal().performClick()
        compose.onNodeWithText(text(R.string.recovery)).performClick()
        screen("04-stage")
        compose.onNodeWithTag("save-stage").reveal().performClick()
        compose.onNodeWithTag("save-plan").reveal().performClick()
        compose.waitUntil(30000) { c.repo.workouts.value.any { it.ownerUserId==a && it.title=="午后间歇" } }
        val plan=c.repo.workouts.value.last { it.ownerUserId==a && it.title=="午后间歇" }
        assertEquals(HintMode.OFF,plan.steps.last().heart.mode); assertEquals(HintMode.OFF,plan.steps.last().frequency.mode)
        page(1)
        compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }
        screen("05-history-week")
        openFixture(Machine.ELLIPTICAL)
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-scroll").fetchSemanticsNodes().isNotEmpty() }; screen("06-report")
        compose.onNodeWithTag("detail-scroll").performScrollToNode(hasText(text(R.string.report_performance)))
        screen("07-report-metrics")
        assertFalse(compose.onAllNodesWithText(text(R.string.strokes)).fetchSemanticsNodes().any())
        page(3); screen("08-settings")
        compose.runOnUiThread { compose.activity.settingsSection.value="users" }; screen("09-users")
        page(0); compose.onNodeWithTag("current-user").performClick(); screen("10-user-picker")
        compose.onNodeWithTag("add-user").reveal().performClick()
        compose.onNodeWithTag("user-name").performTextInput(addedName)
        screen("11-user-ime")
        UiDevice.getInstance(inst).pressBack(); compose.onNodeWithTag("save-user").reveal().performClick(); screen("12-user-body")
        compose.onNodeWithTag("user-weight").reveal().performTextInput("68")
        UiDevice.getInstance(inst).pressBack(); compose.onNodeWithTag("save-user").reveal().performClick()
        compose.waitUntil(30000) { c.repo.users.value.any { it.name==addedName } }
        screen("13-user-added")
    }
    @Test fun refinementScreensAndSixMetrics() {
        val ids=listOf(MetricId.CADENCE,MetricId.HEART,MetricId.DISTANCE,MetricId.CALORIES,MetricId.POWER,MetricId.SPEED)
        val cfg=compose.activity.resources.configuration
        compose.runOnUiThread { c.ble.state.value=org.openmobifitness.app.ble.LinkState(name="MB-EP-177258 · UI fixture",address="00:00:00:03:00:01",phase="ready",machine=Machine.ELLIPTICAL,protocol=Protocol.V1,range=ResistanceRange(1.0,24.0),writable=true) }
        page(2); screen("refine-devices")
        compose.runOnUiThread { c.ble.state.value=org.openmobifitness.app.ble.LinkState() }
        page(3); compose.onNodeWithTag("setting-theme").assertIsDisplayed(); screen("refine-settings")
        page(1)
        compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }
        openFixture(Machine.ELLIPTICAL)
        compose.waitUntil(30000) { compose.onAllNodesWithTag("detail-scroll").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("detail-scroll").performScrollToNode(hasText(text(R.string.report_performance)))
        screen("refine-report")
        compose.onNodeWithTag("detail-scroll").performScrollToNode(hasText(text(R.string.power)) and isSelectable())
        compose.onAllNodesWithText(text(R.string.power)).filter(isSelectable()).onFirst().assertIsSelected()
        if(args.getString("screenGroup")=="pages") return
        page(0)
        compose.runOnUiThread { c.setIdentityPolicy(IdentityPolicy.REMEMBER); c.display.save(DisplayScope.TRAINING,ids,Machine.ELLIPTICAL); c.local.savePresets("demo.ELLIPTICAL",Machine.ELLIPTICAL,ResistanceRange(1.0,24.0),listOf(4.0,8.0,12.0,18.0)); c.select(null); compose.activity.startTraining() }
        compose.waitUntil(60000) { c.state.value.session!=null }
        compose.onNodeWithTag("finish").assertIsDisplayed()
        if(cfg.screenWidthDp>=1200 && cfg.fontScale<=1.1f) ids.forEach { compose.onNodeWithTag("live-metric-${it.name}").assertIsDisplayed() }
        screen("refine-free")
        runBlocking { c.finish().join() }; page(0)
        compose.runOnUiThread { c.select(WorkoutPolicy.templates(Machine.ELLIPTICAL).first { it.id=="steady20_elliptical" }); compose.activity.startTraining() }
        compose.waitUntil(60000) { c.state.value.session!=null }
        compose.onNodeWithTag("finish").assertIsDisplayed()
        if(cfg.screenWidthDp>=1200 && cfg.fontScale<=1.1f) {
            ids.forEach { compose.onNodeWithTag("live-metric-${it.name}").assertIsDisplayed() }
            compose.onNodeWithTag("metric-page-count").assertIsDisplayed().assertTextEquals("1 / 1")
            compose.onNodeWithTag("metric-page-indicators").assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.previous_metrics)).assertIsNotEnabled()
            compose.onNodeWithContentDescription(text(R.string.next_metrics)).assertIsNotEnabled()
            compose.onNodeWithTag("preset-18.0").assertIsDisplayed()
            val levels=listOf(4.0,8.0,12.0,18.0).map { compose.onNodeWithTag("preset-$it").fetchSemanticsNode().boundsInRoot }
            assertTrue("Four quick levels fit on one row",levels.all { kotlin.math.abs(it.top-levels.first().top)<1f })
        }
        screen("refine-training")
        compose.onNodeWithContentDescription(text(R.string.increase)).reveal().assertIsDisplayed()
        screen("refine-controls")
        compose.onNodeWithTag("preset-18.0").reveal().assertIsDisplayed()
        val presetBounds=listOf(4.0,8.0,12.0,18.0).map { compose.onNodeWithTag("preset-$it").fetchSemanticsNode().boundsInRoot }
        assertTrue("Quick levels stay on one row at every tested width/font",presetBounds.all { kotlin.math.abs(it.top-presetBounds.first().top)<1f })
        runBlocking { c.finish().join() }; page(0)
        compose.runOnUiThread { c.setDemo(false); c.select(null) }
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-warmup_elliptical"))
        compose.onNodeWithTag("plan-warmup_elliptical").performClick()
        compose.onNodeWithTag("start-plan").assertTextContains(text(R.string.plan_device_disconnected))
        screen("refine-disconnected")
        compose.onNodeWithTag("start-plan").performClick()
        assertEquals(2,compose.activity.page.value)
        compose.onNodeWithTag("start-plan").assertDoesNotExist()
    }

    @Test fun newMachinePresetsExposeTheirActualTargets() {
        compose.runOnUiThread { c.setIdentityPolicy(IdentityPolicy.REMEMBER) }
        for(machine in listOf(Machine.ROWER,Machine.TREADMILL)) {
            page(0)
            compose.runOnUiThread { c.setDemo(true,machine); c.select(WorkoutPolicy.templates(machine).first { it.id.startsWith("steady20_") }); compose.activity.startTraining() }
            compose.waitUntil(60000) { c.state.value.session!=null }
            compose.runOnUiThread { c.state.value=c.state.value.copy(session=c.state.value.session!!.copy(elapsedMs=301000)) }
            compose.waitUntil(30000) { c.state.value.stage==1 }
            compose.onNodeWithTag("finish").assertIsDisplayed()
            assertEquals(0,compose.onAllNodesWithContentDescription(text(R.string.increase)).fetchSemanticsNodes().size)
            screen("refine-${machine.name.lowercase()}-target")
            runBlocking { c.finish().join() }
        }
        page(0)
        compose.runOnUiThread { c.setDemo(true,Machine.BIKE); c.select(null) }
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-equipment"))
        compose.onNodeWithTag("plan-equipment").performClick()
        compose.onNode(hasText(text(R.string.machine_elliptical)) and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-warmup_elliptical")); compose.onNodeWithTag("plan-warmup_elliptical").performClick()
        compose.onNodeWithTag("start-plan").assertTextContains(text(R.string.plan_device_mismatch))
        screen("refine-mismatch"); compose.onNodeWithTag("start-plan").performClick(); assertEquals(2,compose.activity.page.value)
    }

    @Test fun matrixScreens() {
        screen("matrix-home")
        compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-warmup_elliptical")); screen("matrix-cards")
        if(args.getString("expectedColumns")!=null) {
            val expected=args.getString("expectedColumns")!!.toInt(); val ids=listOf("warmup","recovery","steady20").take(expected)
            val nodes=ids.map { compose.onNodeWithTag("plan-${it}_elliptical").fetchSemanticsNode().boundsInRoot }
            assertTrue(nodes.all { kotlin.math.abs(it.top-nodes.first().top)<2f }); assertEquals(expected,nodes.map { it.left }.distinct().size)
        }
        page(1); compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }; screen("matrix-history")
        compose.onNodeWithTag("history-owner").performClick(); screen("matrix-scope"); UiDevice.getInstance(inst).pressBack()
        page(0); compose.onNodeWithTag("current-user").performClick(); screen("matrix-picker"); UiDevice.getInstance(inst).pressBack()
        page(3); screen("matrix-settings")
    }
    @Test fun rowerAndTreadmillUseTheirOwnStageSemantics() {
        listOf(Machine.ROWER to R.string.machine_rower,Machine.TREADMILL to R.string.machine_treadmill).forEach { (machine,label) ->
            compose.onNodeWithTag("plan-library").performScrollToNode(hasTestTag("plan-equipment"))
            compose.onNodeWithTag("plan-equipment").performClick(); compose.onNodeWithText(text(label)).performClick()
            compose.onNodeWithTag("new-plan").reveal().performClick()
            val title="v030 native ${machine.name}"
            compose.onNodeWithTag("plan-title").performTextReplacement(title); compose.onNodeWithTag("plan-title").performImeAction()
            compose.onNodeWithTag("add-stage").reveal().performClick()
            compose.onAllNodesWithText(text(R.string.stage_training)).onLast().performClick()
            if(machine==Machine.ROWER) {
                compose.onNodeWithText(text(R.string.strokes)).reveal().performClick()
                compose.onNodeWithTag("stage-target").performTextReplacement("60")
                UiDevice.getInstance(inst).pressBack()
                compose.onNodeWithText(text(R.string.resistance_percent)).assertDoesNotExist()
            } else {
                compose.onNodeWithText(text(R.string.strokes)).assertDoesNotExist()
                compose.onNodeWithContentDescription(text(R.string.stage_speed_target)).reveal().performClick()
                compose.onNodeWithTag("stage-speed").reveal().performTextInput("6")
                screen("typed-treadmill-ime")
                UiDevice.getInstance(inst).pressBack()
                compose.onNodeWithText(text(R.string.resistance_percent)).assertDoesNotExist()
            }
            screen("typed-${machine.name.lowercase()}")
            compose.onNodeWithTag("save-stage").reveal().performClick(); compose.onNodeWithTag("save-plan").reveal().performClick()
            compose.waitUntil(30000) { c.repo.workouts.value.any { it.title==title && it.ownerUserId==a } }
            val step=c.repo.workouts.value.last { it.title==title }.steps.last()
            if(machine==Machine.ROWER) { assertEquals(org.openmobifitness.core.Condition.STROKES,step.condition); assertEquals(60.0,step.target,0.0) }
            else { assertEquals(6.0/3.6,step.speedTargetMps!!,.000001); assertNull(step.resistancePercent) }
        }
    }
    @Test fun debugSampleButtonsLoadAndPreciselyClearTheFormalDataset() {
        page(2)
        compose.onNodeWithTag("device-list").performScrollToNode(hasTestTag("debug-load-samples"))
        val beforeLoad=c.repo.revision.value
        compose.onNodeWithTag("debug-load-samples").performClick()
        compose.waitUntil(360000) { c.repo.revision.value>beforeLoad && c.repo.users.value.any { it.id==DebugDataset.userIds[0] && it.available } && !c.repo.maintenance.value }
        assertEquals(780,runBlocking(Dispatchers.IO) { DebugSampleStore(c.repo).info().sessions })
        screen("debug-loaded")
        compose.onNodeWithTag("debug-view-samples").reveal().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithTag("history-loading").fetchSemanticsNodes().isEmpty() }; screen("debug-history")
        page(2); compose.onNodeWithTag("device-list").performScrollToNode(hasTestTag("debug-clear-samples"))
        // Returning to Devices recreates the panel; wait for its asynchronous count refresh.
        compose.waitUntil(90000) { compose.onAllNodes(hasTestTag("debug-clear-samples") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen("debug-before-clear")
        compose.onNodeWithTag("debug-clear-samples").performClick()
        compose.waitUntil(90000) { compose.onAllNodesWithText(text(R.string.confirm)).fetchSemanticsNodes().isNotEmpty() }; screen("debug-clear-preview")
        compose.onNodeWithText(text(R.string.confirm)).performClick()
        compose.waitUntil(360000) { c.repo.users.value.firstOrNull { it.id==DebugDataset.userIds[0] }?.available==false && !c.repo.maintenance.value }
        assertEquals(0,runBlocking(Dispatchers.IO) { DebugSampleStore(c.repo).info().sessions })
        runBlocking { assertNotNull(c.repo.history.session(id(Machine.ELLIPTICAL))) }
        assertEquals(a,c.currentUser.value?.id); screen("debug-cleared")
    }
    @Test fun localPhotoPickerPreviewsThenPersistsPrivateAvatar() {
        val resolver=compose.activity.contentResolver
        val uri=resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"v030-avatar.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
        })!!
        val bitmap=android.graphics.Bitmap.createBitmap(640,400,android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply { drawColor(android.graphics.Color.rgb(54,117,152)); drawCircle(320f,170f,95f,android.graphics.Paint().apply { color=android.graphics.Color.rgb(247,200,121) }) }
        resolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        try {
            compose.onNodeWithTag("current-user").performClick(); compose.onNodeWithTag("add-user").reveal().performClick()
            compose.onNodeWithTag("user-name").performTextReplacement(addedName); screen("photo-name-ime")
            compose.onNodeWithTag("user-name").assertTextContains(addedName)
            UiDevice.getInstance(inst).pressBack(); screen("photo-before-picker")
            compose.onNodeWithTag("user-name").assertTextContains(addedName)
            compose.onNodeWithTag("choose-avatar").reveal().performClick()
            val device=UiDevice.getInstance(inst)
            val picked=device.wait(Until.findObject(By.descStartsWith("Photo taken on")),5000)
                ?: device.wait(Until.findObject(By.text("v030-avatar.png")),15000)
                ?: device.wait(Until.findObject(By.descContains("v030-avatar")),5000)
            if(picked==null) { device.dumpWindowHierarchy(File(compose.activity.getExternalFilesDir(null),"photo-picker.xml")); screen("photo-picker-unresolved") }
            assertNotNull("Local image should be visible in the system document picker",picked); picked!!.click()
            compose.waitUntil(30000) { compose.onAllNodesWithText(text(R.string.user_photo_apply)).fetchSemanticsNodes().isNotEmpty() }; screen("photo-preview")
            compose.onNodeWithText(text(R.string.user_photo_apply)).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.user_photo_apply)).fetchSemanticsNodes().isEmpty() }; screen("photo-selected")
            compose.onNodeWithTag("save-user").reveal().performClick(); screen("photo-body")
            compose.onNodeWithTag("user-weight").assertExists()
            compose.onNodeWithTag("save-user").reveal().performClick(); screen("photo-after-save")
            compose.waitUntil(30000) { c.repo.users.value.any { it.name==addedName && it.avatar.isNotEmpty() } }
            val user=c.repo.users.value.first { it.name==addedName }; assertNull(user.weightKg)
            c.repo.avatars.verify(user.avatar,c.repo.avatars.file(user.avatar).readBytes()); screen("photo-saved")
        } finally { resolver.delete(uri,null,null) }
    }
    @Test fun startConfirmationAndDestructiveRemovalStayExplicit() {
        compose.onNodeWithTag("hero-start").reveal().performClick()
        compose.waitUntil(30000) { c.pendingStart.value!=null }; assertNull(c.state.value.session); screen("14-start-confirmation")
        compose.onNodeWithTag("user-$b").reveal().performClick(); compose.onNodeWithTag("confirm-user").reveal().performClick()
        compose.waitUntil(30000) { c.state.value.session!=null }; assertEquals(b,c.state.value.session!!.startedUserId)
        compose.runOnUiThread { c.pauseResume() }; compose.waitUntil(30000) { c.state.value.paused }; assertTrue(c.identityLocked); screen("15-training-paused")
        runBlocking { c.finish().join() }; compose.waitUntil(30000) { c.finishedSession.value!=null }; screen("16-saved-report")
        page(3); compose.runOnUiThread { compose.activity.settingsSection.value="users" }
        compose.onAllNodesWithText(text(R.string.user_remove)).onFirst().reveal().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithTag("confirm-remove-user").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.user_remove_delete)).reveal().performClick()
        compose.onNodeWithTag("confirm-remove-user").assertIsNotEnabled(); screen("17-delete-warning")
        compose.onNodeWithTag("confirm-delete-data").reveal().performClick(); compose.onNodeWithTag("confirm-remove-user").assertIsEnabled()
        screen("18-delete-confirmed")
        // Preview only: retain fixtures for the remaining matrix runs.
    }
}
