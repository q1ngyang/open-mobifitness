package org.openmobifitness.core

import org.junit.Test
import org.junit.Assert.*

class RangeHintTest {
    @Test fun dwellHysteresisAndCooldownPreventRepeatedBoundaryAlerts() {
        val e=RangeHint(); val range=PersonalRange(true,60.0,90.0)
        assertFalse(e.update(0,59.0,range,true).notify)
        assertFalse(e.update(4999,59.0,range,true).notify)
        assertTrue(e.update(5000,59.0,range,true).notify)
        assertEquals(RangePosition.BELOW,e.update(6000,61.0,range,true).position)
        assertFalse(e.update(34999,59.0,range,true).notify)
        assertTrue(e.update(35000,59.0,range,true).notify)
        assertEquals(RangePosition.WITHIN,e.update(36000,62.0,range,true).position)
        assertFalse(e.update(37000,100.0,range,true).notify)
        assertFalse(e.update(42000,100.0,range,true).notify)
        assertTrue(e.update(65000,100.0,range,true).notify)
    }
    @Test fun pauseDisconnectAndMissingReadingsRequireFreshDwell() {
        val range=PersonalRange(true,60.0,null)
        listOf(false to 40.0,true to null).forEach { (active,value) ->
            val e=RangeHint(); e.update(0,40.0,range,true)
            assertEquals(RangePosition.UNAVAILABLE,e.update(4000,value,range,active).position)
            assertFalse(e.update(5000,40.0,range,true).notify)
            assertTrue(e.update(10000,40.0,range,true).notify)
        }
    }
    @Test fun defaultsDisabledAndNarrowRangesCanRecover() {
        val e=RangeHint()
        assertEquals(HintResult(),e.update(0,200.0,PersonalRange(),true))
        val range=PersonalRange(true,60.0,61.0)
        e.update(0,59.0,range,true)
        assertEquals(RangePosition.WITHIN,e.update(1000,60.5,range,true).position)
        assertEquals(RangePosition.WITHIN,e.update(2000,61.0,range,true).position)
        assertEquals(RangePosition.ABOVE,e.update(3000,62.0,range,true).position)
    }
    @Test(expected=IllegalArgumentException::class) fun noEnabledRangeWithoutBounds() { PersonalRange(true) }
}
