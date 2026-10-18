package org.openmobifitness.app

import android.app.Application
import android.os.SystemClock
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.ble.BleClient
import org.openmobifitness.app.ble.LinkState
import org.openmobifitness.app.data.saveUser
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers

/** v0.3.3 observations, NOT desired behavior. Passing confirms a gap still exists.
 * Synthetic packets exercise real parsing, merging, controller timing and storage.
 * No radio, official DEX execution or mechanical validation is performed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class V033GapAuditTest {
    private lateinit var c: Controller
    private val now get() = SystemClock.elapsedRealtime()

    @Before fun setup() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("openmobi.db")
        app.getSharedPreferences("preferences", 0).edit().clear().commit()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        c = Controller(app, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), tickAutomatically=false)
        withTimeout(5000) { c.state.first { it.ready } }
        val user = UserProfile(name="Protocol audit")
        c.repo.saveUser(user)
        c.switchUser(user.id).join()
        c.setIdentityPolicy(IdentityPolicy.REMEMBER)
    }

    @After fun cleanup() {
        if (::c.isInitialized) {
            c.scope.cancel(); c.ble.disconnect(); c.heart.disconnect(); c.repo.db.close()
        }
    }

    private fun receive(id: String, bytes: ByteArray) {
        BleClient::class.java.getDeclaredMethod("receive", UUID::class.java, ByteArray::class.java)
            .apply { isAccessible=true }.invoke(c.ble, BleClient.uuid(id), bytes)
    }
    private fun bind(protocol: Protocol, machine: Machine, name: String="Audit"): MobiSession? {
        c.ble.state.value = LinkState(name=name, phase="ready", protocol=protocol, machine=machine)
        val mobi = if (protocol in setOf(Protocol.V1, Protocol.V2, Protocol.HUANTONG)) MobiSession(protocol, name) else null
        ReflectionHelpers.setField(c.ble, "mobi", mobi)
        return mobi
    }
    private suspend fun tick(ms: Long=1000) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); c.tick()
    }
    private fun atr(counter: Int) = byteArrayOf(0xab.toByte(),4,1,10,1,100,1,0,0,3,0xe8.toByte(),(counter ushr 8).toByte(),counter.toByte())
    private fun observed(id: String, detail: String) = println("AUDIT $id: $detail")

    @Test fun atrCounterBytesDoNotReachMetricsOrDistance() {
        val m = MobiSession(Protocol.V1, "AT-R66146")
        val first = m.receive("ffe4", atr(100))!!
        val next = m.receive("ffe4", atr(110))!!
        assertEquals(first, next)
        assertNull(next.distanceM)
        assertEquals(60.0, next.cadence!!, 0.0)
        observed("A01", "counter 100 -> 110 produces identical Metrics; distance remains null")
    }

    @Test fun atrSubtypeTableBlocksResistanceCommand() {
        val m = MobiSession(Protocol.V1, "AT-R66146")
        m.receive("ffe4", atr(1))
        assertEquals(0, m.resistanceMode)
        assertEquals(ResistanceRange(1.0,24.0), m.range)
        assertFalse(m.canWriteResistance)
        assertNull(m.resistanceCommand(1))
        observed("A02", "subtype 1 remains mode 0; resistance command is unavailable")
    }

    @Test fun silentAtrKeepsIntegratingUntilTheFiveSecondCutoff() = runBlocking {
        bind(Protocol.V1, Machine.BIKE, "AT-R66146")
        receive("ffe4", atr(100)); c.tick(); c.start().join()
        assertNotNull(c.state.value.session)
        repeat(4) { tick() }
        val excess = c.state.value.session!!.distanceM!!
        assertEquals(4 * 60.0 / 2.68 / 3.6, excess, 1e-8)
        tick(); assertNull(c.state.value.metrics.cadence)
        tick(5000)
        assertEquals(excess, c.state.value.session!!.distanceM!!, 0.0)
        c.finish().join()
        assertEquals(excess, c.repo.archive().sessions.single().distanceM!!, 0.0)
        observed("A03", "60 rpm + silence adds $excess m in four ticks; cutoff at 5000 ms works")
    }

    @Test fun freshFtmsCadenceKeepsAnOldSpeedAlive() = runBlocking {
        bind(Protocol.FTMS, Machine.BIKE)
        // Speed 10 m/s + cadence 60 rpm, then cadence-only with More Data bit.
        receive("2ad2", byteArrayOf(4,0,0x10,0x0e,120,0))
        c.tick(); c.start().join()
        repeat(6) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            receive("2ad2", byteArrayOf(5,0,0,0)); c.tick()
        }
        assertEquals(0.0, c.state.value.metrics.cadence!!, 0.0)
        assertEquals(10.0, c.state.value.metrics.speedMps!!, 0.0)
        assertEquals(60.0, c.state.value.session!!.distanceM!!, 0.0)
        assertEquals(6, c.repo.archive().samples.size)
        observed("A04", "six cadence-only packets retain a six-second-old speed and record 60 m")
    }

    @Test fun partialFtmsUpdatesKeepOldSpeedAcrossFourEquipmentFamilies() = runBlocking {
        data class Case(val machine: Machine, val chr: String, val full: ByteArray, val partial: ByteArray, val speed: Double, val official: Boolean=false)
        val cases=listOf(
            Case(Machine.BIKE,"2ad2",byteArrayOf(4,0,16,14,120,0),byteArrayOf(5,0,0,0),10.0),
            Case(Machine.ELLIPTICAL,"2ace",byteArrayOf(8,0,0,16,14,120,0,0,0),byteArrayOf(9,0,0,0,0,0,0),10.0),
            Case(Machine.ROWER,"2ad1",byteArrayOf(8,0,60,12,0,100,0),byteArrayOf(0,0,0,13,0),5.0),
            Case(Machine.TREADMILL,"2acd",byteArrayOf(32,0,16,14,120),byteArrayOf(33,0,0),10.0,true)
        )
        for (case in cases) {
            bind(Protocol.FTMS,case.machine,if(case.official) "MOBI-T" else "Audit")
            ReflectionHelpers.setField(c.ble,"officialFtms",case.official)
            ReflectionHelpers.setField(c.ble,"mobiFtms",if(case.official) MobiFtmsSession("MOBI-T") else null)
            receive(case.chr,case.full);c.tick();c.start().join()
            assertNotNull(c.state.value.session)
            repeat(6) {
                ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
                receive(case.chr,case.partial);c.tick()
            }
            assertEquals(case.speed,c.state.value.metrics.speedMps!!,0.0)
            assertEquals(case.speed*6,c.state.value.session!!.distanceM!!,1e-8)
            val id=c.state.value.session!!.id
            c.finish().join()
            assertEquals(case.speed*6,c.repo.archive().sessions.single { it.id==id }.distanceM!!,1e-8)
            observed("A11","${case.machine}: partial ${case.chr} updates preserve speed ${case.speed}; saved ${case.speed*6} m")
        }
    }

    @Test fun extremeFtmsCadenceIsSavedWithoutPlausibilityFiltering() = runBlocking {
        bind(Protocol.FTMS,Machine.BIKE)
        receive("2ad2",byteArrayOf(5,0,0xff.toByte(),0xff.toByte()))
        c.tick();c.start().join();tick()
        assertEquals(32767.5,c.state.value.metrics.cadence!!,0.0)
        assertEquals(32767.5,c.repo.archive().samples.single().metrics.cadence!!,0.0)
        observed("A12","synthetic FTMS cadence 0xffff is recorded as 32767.5 rpm; no physiological/device plausibility filter")
    }

    @Test fun modeThreeRowingPulsesRefreshOldAggregateValues() = runBlocking {
        bind(Protocol.V2, Machine.ROWER)
        receive("8802", byteArrayOf(9,3)); receive("8801", byteArrayOf(3))
        receive("8813", byteArrayOf(30,0,24,0,30,0,100,0,30,0,200.toByte(),0,12))
        val aggregateAt = c.ble.state.value.motionAt
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        receive("8811", byteArrayOf(1,0xf4.toByte(),0,12)); c.tick()
        assertTrue(c.ble.state.value.motionAt > aggregateAt)
        assertEquals(3.0, c.state.value.metrics.speedMps!!, 0.0)
        assertEquals(200.0, c.state.value.metrics.powerW!!, 0.0)
        observed("A05", "8811 refreshes the age of speed/power last supplied by 8813 six seconds earlier")
    }

    @Test fun treadmillStrideSurvivesZeroSpeedAndSensorExpiry() = runBlocking {
        bind(Protocol.V1, Machine.TREADMILL)
        val moving = byteArrayOf(0xab.toByte(),4,0,12,18,1,0,72,0,0,120)
        receive("ffe4", moving); c.tick(); c.start().join()
        assertEquals(1.0, c.state.value.metrics.strideM!!, 0.0)
        receive("ffe4", moving.copyOf().apply { this[7]=0; this[10]=0 })
        tick(6000)
        assertNull(c.state.value.metrics.speedMps)
        assertNull(c.state.value.metrics.stepRate)
        assertEquals(1.0, c.state.value.metrics.strideM!!, 0.0)
        assertEquals(1.0, c.repo.archive().samples.single().metrics.strideM!!, 0.0)
        observed("A06", "stride remains 1 m and is saved after speed/step rate expire")
    }

    @Test fun wrappedCountersAreTreatedAsResetAndLoseTheWrapDelta() {
        // V2 counts/distance are 16 bit; FTMS treadmill steps are 24 bit.
        val totals = TelemetryTotals(Metrics(strokes=65534, stepCount=16777214, jumpCount=65534, repetitions=65534, distanceM=65534.0))
        for (v in listOf(1,2)) totals.update(Metrics(strokes=v,stepCount=v,jumpCount=v,repetitions=v,distanceM=v.toDouble()),1000,true,Machine.ROWER,Protocol.V2,70.0,5.0)
        assertEquals(1, totals.strokes); assertEquals(1, totals.steps)
        assertEquals(1, totals.jumps); assertEquals(1, totals.repetitions)
        assertEquals(1.0, totals.distanceM!!, 0.0)
        observed("A07", "16/24-bit max-1 -> 1 -> 2 accumulates 1; a wrapping counter would accumulate 4")
    }

    @Test fun zeroMagnetMetadataGeneratesPlausibleCadenceInTheLiveParser() {
        val m = MobiSession(Protocol.V2, "Audit")
        m.receive("8802", byteArrayOf(10,18)); m.receive("8801", byteArrayOf(1))
        m.receive("8805", byteArrayOf(0))
        val pulse = byteArrayOf(3,0xe8.toByte())
        assertNull(Protocols.intervalCadence(pulse,0))
        assertEquals(60.0, m.receive("8811",pulse)!!.cadence!!, 0.0)
        observed("A08", "helper rejects magnets=0; live session turns division by zero into 60 rpm")
    }

    @Test fun aggregateDistanceWorkoutIsRejectedBeforeReportingCanStart() = runBlocking {
        for ((machine,condition) in listOf(Machine.BIKE to Condition.DISTANCE,Machine.ROWER to Condition.STROKES)) {
            bind(Protocol.V2, machine)
            receive("8802", if(machine==Machine.BIKE) byteArrayOf(10,18) else byteArrayOf(9,3))
            receive("8801", byteArrayOf(2)); c.tick()
            c.select(Workout(title="Measured target", machine=machine, ownerUserId=c.currentUser.value!!.id,
                steps=listOf(Step(condition=condition, target=100.0))))
            c.start().join()
            assertNull(c.state.value.session)
            assertEquals(R.string.sensor_required, c.state.value.error)
        }
        observed("A09", "ready mode-2 distance/stroke plans fail sensor validation before beginLiveUse")
    }

    @Test fun batteryReadIsStillDiscardedByTheApplication() {
        for (protocol in listOf(Protocol.V2,Protocol.FTMS)) {
            bind(protocol, Machine.BIKE)
            receive("2a19", byteArrayOf(87))
            assertNull(c.ble.state.value.deviceDetails.battery)
        }
        observed("A10", "V2/FTMS 2A19=87 does not enter LinkState.deviceDetails")
    }

    @Test fun explicitV2CapabilityDoesOverrideTheSubtypeDefault() {
        val m = MobiSession(Protocol.V2,"Audit")
        m.receive("8806",byteArrayOf(4,1,24)); m.receive("8802",byteArrayOf(10,1))
        assertTrue(m.canWriteResistance); assertFalse(m.canReadResistance)
        assertArrayEquals(byteArrayOf(2,1,1),m.resistanceCommand(1))
        observed("CONTROL-1", "explicit V2 write-only capability survives later subtype identification")
    }

    @Test fun heartOnlyUpdatesDoNotRefreshMotion() {
        bind(Protocol.FTMS, Machine.BIKE)
        receive("2ad2",byteArrayOf(4,0,0x10,0x0e,120,0))
        val motionAt = c.ble.state.value.motionAt
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        receive("2a37",byteArrayOf(0,130.toByte()))
        assertEquals(motionAt,c.ble.state.value.motionAt)
        observed("CONTROL-2", "heart-only updates do not extend motion freshness")
    }

    @Test fun counterResetAndPauseRebaseRemainIntentional() {
        val totals = TelemetryTotals(Metrics(strokes=100))
        totals.update(Metrics(strokes=0),1000,true,Machine.ROWER,Protocol.V2,70.0,5.0)
        totals.update(Metrics(strokes=2),1000,true,Machine.ROWER,Protocol.V2,70.0,5.0)
        assertEquals(2,totals.strokes)
        totals.rebase()
        totals.update(Metrics(strokes=200),1000,true,Machine.ROWER,Protocol.V2,70.0,5.0)
        assertEquals(2,totals.strokes)
        observed("CONTROL-3", "reset and reconnect rebase do not charge offline counts; wrap support must preserve this")
    }
}
