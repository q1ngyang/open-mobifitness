package org.openmobifitness.app.data

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openmobifitness.app.DisplayPreferences
import org.openmobifitness.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class HistoryStoreTest {
    private lateinit var repo: Repository
    @Before fun start() { repo=Repository(RuntimeEnvironment.getApplication()) }
    @After fun end() { repo.db.close() }
    @Test fun archiveIsReversibleAndNeverChangesOverview()=runBlocking {
        val s=Session(status="completed",elapsedMs=60000,caloriesKcal=12.0,workoutTitle="test")
        repo.save(s)
        val before=repo.history.overview(HistoryQuery())
        repo.setArchived(s.id,true)
        assertEquals(1,repo.history.page(HistoryQuery()).count) // Old archive flags no longer hide records.
        assertEquals(0,repo.history.page(HistoryQuery(archive=0)).count)
        assertEquals(s.id,repo.history.page(HistoryQuery(archive=1)).rows.single().id)
        assertEquals(before,repo.history.overview(HistoryQuery()))
        repo.setArchived(s.id,false)
        assertEquals(s.id,repo.history.page(HistoryQuery()).rows.single().id)
    }
    @Test fun paginationFiltersAndLiteralSearchWorkOnLargeLibrary()=runBlocking {
        val rows=(0 until 1101).map { i -> Session(start=Instant.ofEpochMilli(1700000000000+i*86400000L).toString(),status="completed",machine=if(i%2==0) Machine.ELLIPTICAL else Machine.ROWER,demo=i%3==0,workoutTitle=if(i%10==0) "100%_interval" else "steady",elapsedMs=60000,caloriesKcal=5.0) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.db.runInTransaction { rows.forEach { repo.db.records().session(it.row()) } } }
        val first=repo.history.page(HistoryQuery(),0,20); val next=repo.history.page(HistoryQuery(),20,20)
        assertEquals(1101,first.count); assertEquals(20,first.rows.size); assertTrue(first.rows.map { it.id }.intersect(next.rows.map { it.id }.toSet()).isEmpty())
        val matches=repo.history.page(HistoryQuery(machine="ELLIPTICAL",search="%_",source=1))
        assertEquals(rows.count { it.machine==Machine.ELLIPTICAL && it.workoutTitle.contains("%_") && !it.demo },matches.count)
        assertEquals(1101L*60000,repo.history.overview(HistoryQuery()).elapsedMs)
        repo.load(); assertTrue(repo.sessions.value.size<=100)
        val query=HistoryQuery(search="%_")
        val count=repo.archiveMatching(query)
        assertEquals(111,count); assertEquals(111,repo.history.page(query.copy(archive=1)).count)
        assertEquals(1101,repo.history.overview(HistoryQuery()).count)
    }
    @Test fun checkpointDoesNotRefreshLibraryAndFinishedSessionCannotBeResurrected()=runBlocking {
        val active=Session(elapsedMs=1000)
        repo.save(active); val revision=repo.revision.value
        repo.save(active.copy(elapsedMs=2000),Sample(active.id,2000,Metrics(cadence=50.0)))
        assertEquals(revision,repo.revision.value)
        repo.save(active.copy(elapsedMs=2000,status="completed"))
        repo.save(active.copy(elapsedMs=3000),Sample(active.id,3000,Metrics(cadence=60.0)))
        assertEquals("completed",repo.history.detail(active.id)!!.first.status)
        assertEquals(1,repo.history.detail(active.id)!!.second.samples)
    }
    @Test fun readableCsvHasOneSummaryRowPerWorkoutAndNoProtocolDump()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val s=Session(start="2026-09-28T01:00:00Z",zone="Asia/Singapore",elapsedMs=120000,status="completed",device="=cmd",workoutTitle="HIIT, 30",caloriesKcal=12.5,distanceM=200.0,archived=true)
        repo.save(s,Sample(s.id,1000,Metrics(heartBpm=140,powerW=120.0,powerEstimated=true)))
        val output=ByteArrayOutputStream()
        StatisticsReport.write(context,repo.history,HistoryQuery(archive=2),null,output,false)
        val csv=output.toString("UTF-8"); val rows=Csv.read(csv)
        assertEquals(2,rows.size); assertTrue(rows[1][0].startsWith("2026-09-28 09:00:00")); assertEquals("00:02:00",rows[1][5]); assertEquals("'=cmd",rows[1][4]); assertEquals("HIIT, 30",rows[1][3]); assertTrue(csv.contains("bpm")); assertFalse(csv.contains("elapsed_ms")); assertFalse(csv.contains("session_id"))
        assertTrue(rows[1].contains("—")); assertEquals(rows[0].size,rows[1].size)
    }
    @Test fun dateAliasesLocalizedTypesAndQueryRestorationMatchTheSameRecord()=runBlocking {
        val start=LocalDate.of(2026,10,2).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toString()
        val s=Session(start=start,status="completed",machine=Machine.ELLIPTICAL,device="MB-EP")
        repo.save(s)
        for(term in listOf("10/2","2026/10/2","2026年10月2日")) assertEquals(s.id,repo.history.page(HistoryQuery(search=term)).rows.single().id)
        val query=HistoryQuery(search="椭圆机",machineWords="ELLIPTICAL")
        assertEquals(query,HistoryQuery.decode(query.encode()))
        assertEquals(s.id,repo.history.page(query).rows.single().id)
        assertEquals(0,repo.history.page(HistoryQuery(search="' OR 1=1 --")).count)
    }
    @Test fun packetLoggingDefaultsOnButExplicitOptOutPersists() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("test-prefs",0)
        prefs.edit().clear().commit(); assertTrue(DisplayPreferences(prefs).packets.value)
        DisplayPreferences(prefs).packetLogs(false); assertFalse(DisplayPreferences(prefs).packets.value)
    }
    @Test fun sixCalendarMonthsAndYearMonthFiltersIncludeOldArchivedData()=runBlocking {
        val zone=ZoneId.of("Europe/Berlin"); val today=LocalDate.of(2026,10,2)
        val recent=RecordDates.recent(today,zone)
        val first=Instant.ofEpochMilli(recent.first).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026,5,1),first)
        val rows=listOf(
            Session(start=Instant.ofEpochMilli(recent.first-1).toString(),status="completed"),
            Session(start=Instant.ofEpochMilli(recent.first).toString(),status="completed",archived=true),
            Session(start=Instant.ofEpochMilli(recent.second-1).toString(),status="completed"),
            Session(start=Instant.ofEpochMilli(recent.second).toString(),status="completed"))
        rows.forEach { repo.save(it) }
        assertEquals(2,repo.history.page(HistoryQuery(recent.first,recent.second)).count)
        assertEquals(4,repo.history.page(HistoryQuery()).count)
        val may=RecordDates.selected(2026,5,zone)
        assertEquals(rows[1].id,repo.history.page(HistoryQuery(may.first,may.second)).rows.single().id)
        val year=RecordDates.selected(2026,0,zone)
        assertEquals(4,repo.history.page(HistoryQuery(year.first,year.second)).count)
        assertEquals(0L to Long.MAX_VALUE,RecordDates.selected(0,0,zone))
    }

}
