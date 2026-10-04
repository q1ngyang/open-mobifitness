package org.openmobifitness.app.data

import org.junit.Test
import org.junit.Assert.*
import org.openmobifitness.core.*
import java.time.*

class DebugDatasetTest {
    private val names=listOf("Demo A","Demo B","Demo C","Demo D")
    @Test fun exactMatrixAndCalendarBoundariesHaveNoFutureDatesInAnyTestZone() {
        assertEquals(780,DebugDataset.entries.size); assertEquals(780,DebugDataset.sessionIds.size)
        val zones=listOf("Asia/Shanghai","America/New_York","Pacific/Kiritimati")
        for(zoneName in zones) for(date in listOf("2026-10-04T11:31:00Z","2027-01-01T00:00:01Z","2028-03-01T06:00:00Z")) {
            val anchor=Instant.parse(date); val zone=ZoneId.of(zoneName)
            val fixtures=DebugDataset.entries.map { DebugDataset.fixture(it,anchor,zone,names) }
            assertTrue(fixtures.sumOf { it.samples.size }<=100000)
            fixtures.forEach { f ->
                assertTrue(Instant.parse(f.session.end)<=anchor)
                assertEquals(f.session.elapsedMs,Duration.between(Instant.parse(f.session.start),Instant.parse(f.session.end)).toMillis())
                assertEquals(0L,f.samples.first().elapsedMs); assertEquals(f.session.elapsedMs,f.samples.last().elapsedMs)
                assertEquals(f.session.distanceM,f.samples.last().metrics.distanceM)
                assertEquals(f.session.caloriesKcal,f.samples.last().metrics.caloriesKcal)
                assertEquals(f.session,Exchange.parse(Exchange.sessions(listOf(f.session))).sessions.single())
            }
            for(user in 0..2) for(machine in DebugDataset.machines) {
                val base=DebugDataset.entries.filter { it.user==user && it.machine==machine && it.boundary==null }.map { DebugDataset.fixture(it,anchor,zone,names).session }
                assertEquals(36,base.size)
                assertEquals(36,base.map { YearMonth.from(Instant.parse(it.start).atZone(zone)) }.distinct().size)
                assertTrue(base.map { Instant.parse(it.start).atZone(zone).year }.distinct().size>=3)
            }
            assertEquals(72,fixtures.count { it.session.ownerUserId==DebugDataset.userIds[3] })
            assertEquals(36,fixtures.count { it.session.ownerUserId==null })
        }
    }
    @Test fun sparseSamplesKeepGapsAndMachineFieldsAreNotInvented() {
        val anchor=Instant.parse("2026-10-04T11:31:00Z"); val zone=ZoneId.of("Asia/Shanghai")
        for(machine in DebugDataset.machines) {
            val fixture=DebugDataset.fixture(DebugDataset.entries.first { it.machine==machine && it.user==1 && it.slot==8 },anchor,zone,names)
            assertEquals(60,fixture.samples.size)
            assertEquals(fixture.samples,Exchange.parse(Exchange.samples(fixture.samples)).samples)
            val stats=Statistics.summarize(fixture.samples)
            assertTrue(stats.metrics.values.all { it.coverageMs < fixture.session.elapsedMs })
            if(machine in setOf(Machine.JUMP_ROPE,Machine.DUMBBELL)) { assertNull(fixture.session.distanceM); assertTrue(fixture.samples.all { it.metrics.speedMps==null && it.metrics.strokes==null }) }
            if(machine==Machine.ROWER) assertTrue(fixture.samples.all { it.metrics.resistance==null && it.metrics.stepCount==null })
        }
    }
}
