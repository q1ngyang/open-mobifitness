package org.openmobifitness.core

import org.junit.Assert.*
import org.junit.Test

class OfficialRowingTest {
    @Test fun originalDexOutputsMatchForAllRowerSubtypesAndStateTransitions() {
        val lines = javaClass.getResourceAsStream("/oracle/rowing-intl-2.1.14.tsv")!!.bufferedReader().readLines().filterNot { it.startsWith('#') }
        val columns = lines.first().split('\t')
        var scenario = ""
        var model = LegacyRowing()
        for (line in lines.drop(1)) {
            val row = line.split('\t')
            if (scenario != row[0]) {
                scenario = row[0]
                model = if (row[2].toBoolean()) LegacyRowing3209() else LegacyRowing()
                model.changeType(row[1].toInt()); model.boat = row[3].toInt()
                if (row[5].toBoolean()) model.reset()
            }
            val input = row[8].toInt()
            when (row[6]) {
                "water" -> model.appendOldWater(input, row[9].toInt())
                "magnet" -> model.appendOldMagnet(input)
                "flag" -> (model as LegacyRowing3209).append(input, row[9].toInt())
                else -> model.append(input, row[4].toBoolean())
            }
            for (i in 10 until row.size) {
                val actual = model.javaClass.getMethod("get${columns[i]}").invoke(model)
                val message = "scenario=$scenario sample=${row[7]} input=$input ${columns[i]}"
                if (actual is Boolean) assertEquals(message, row[i].toBoolean(), actual)
                else {
                    val expected = row[i].toDouble()
                    assertEquals(message, expected, (actual as Number).toDouble(), maxOf(1e-10, kotlin.math.abs(expected) * 1e-12))
                }
            }
        }
    }
}
