package org.openmobifitness.app.data

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ReportResistanceTest {
    private fun record(max: Int)=Session(machine=Machine.ELLIPTICAL,protocol=Protocol.V1,
        capabilitySnapshot="""{"version":1,"machine":"ELLIPTICAL","protocol":"V1","model":"fixture","evidence":"recorded","units":{"resistance":"level"},"resistanceMin":1,"resistanceMax":$max,"resistanceIncrement":1}""")
    private fun resistance(value: Double?)=ReportMetricDescriptor("resistance.average",MetricId.RESISTANCE,value,aggregation=ReportAggregation.AVERAGE,series=SeriesMetric.RESISTANCE)

    @Test fun averagesRetainOneDecimalAndPercentagesUseTheRecordedRangeWithoutChangingStatistics() {
        val descriptor=resistance(7.6)
        assertEquals("7.6" to "32%",descriptor.performanceValue(false,Locale.CHINA,record(24).reportResistanceRange()))
        assertEquals("7.6" to "48%",descriptor.performanceValue(false,Locale.CHINA,record(16).reportResistanceRange()))
        assertEquals("1.0" to "4%",resistance(1.0).performanceValue(false,Locale.CHINA,record(24).reportResistanceRange()))
        assertEquals("24.0" to "100%",resistance(24.0).performanceValue(false,Locale.CHINA,record(24).reportResistanceRange()))
        assertEquals("7,6" to "32%",descriptor.performanceValue(false,Locale.GERMANY,record(24).reportResistanceRange()))
        for(kind in listOf(ReportAggregation.MAXIMUM,ReportAggregation.MINIMUM)) {
            assertEquals("8" to "33%",resistance(8.0).copy(aggregation=kind).performanceValue(false,Locale.CHINA,record(24).reportResistanceRange()))
        }
        assertEquals("7.6" to "",descriptor.reportValue(false,Locale.US))
        assertEquals(7.6,descriptor.value!!,0.0)
    }

    @Test fun legacyMissingOrInvalidRangesNeverInventPercentages() {
        assertNull(Session(machine=Machine.ELLIPTICAL,protocol=Protocol.V1).reportResistanceRange())
        val valid=record(24)
        for(snapshot in listOf("{}",JSONObject(valid.capabilitySnapshot).put("machine","BIKE").toString(),
            JSONObject(valid.capabilitySnapshot).put("resistanceMax",-1).toString(),
            JSONObject(valid.capabilitySnapshot).put("units",JSONObject().put("resistance","torque")).toString())) {
            assertNull(valid.copy(capabilitySnapshot=snapshot).reportResistanceRange())
        }
        assertEquals("8.0" to "",resistance(8.0).performanceValue(false,Locale.US,null))
        assertEquals("—" to "",resistance(null).performanceValue(false,Locale.US,valid.reportResistanceRange()))
    }

    @Test fun otherMetricFormattingIsUnchanged() {
        val speed=ReportMetricDescriptor("speed.average",MetricId.SPEED,9.6,"km/h",ReportAggregation.AVERAGE,SeriesMetric.SPEED)
        assertEquals(speed.reportValue(true,Locale.GERMANY),speed.performanceValue(true,Locale.GERMANY,record(24).reportResistanceRange()))
    }
}
