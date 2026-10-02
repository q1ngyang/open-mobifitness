package org.openmobifitness.app.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openmobifitness.core.*
import java.time.*

/** Archive affects list visibility only. Overview always includes archived workouts. */
data class HistoryQuery(val from: Long=0, val until: Long=Long.MAX_VALUE, val machine: String="", val search: String="", val source: Int=0, val archive: Int=0, val machineWords: String="") {
    fun encode()=JSONObject().put("from",from).put("until",until).put("machine",machine).put("search",search).put("source",source).put("archive",archive).put("machineWords",machineWords).toString()
    companion object { fun decode(text: String?)=runCatching { JSONObject(text!!).let { HistoryQuery(it.getLong("from"),it.getLong("until"),it.getString("machine"),it.getString("search"),it.getInt("source"),it.getInt("archive"),it.optString("machineWords","")) } }.getOrDefault(HistoryQuery()) }
}
data class HistoryPage(val rows: List<Session>, val count: Int)
data class HistoryOverview(val count: Int=0, val elapsedMs: Long=0, val calories: Double?=null, val distanceM: Double?=null, val estimated: Boolean=false, val days: List<Pair<LocalDate,Long>> = emptyList(), val caloriesPresent: Int=0, val demoCount: Int=0)
class HistoryStore(private val db: MobiDatabase) {
    private fun where(q: HistoryQuery, includeArchive: Boolean=false): Pair<String,List<Any>> {
        val clauses=mutableListOf("status!='active'","startEpoch>=?","startEpoch<?")
        val args=mutableListOf<Any>(q.from,q.until)
        if(q.machine.isNotEmpty()) { clauses+="machine=?"; args+=q.machine }
        if(q.search.isNotBlank()) {
            // Literal substring search: user % and _ must not turn into SQL wildcards.
            val matching=q.machineWords.split(',').filter { word -> Machine.entries.any { it.name==word } }
            clauses+="(instr(lower(device),lower(?))>0 OR instr(lower(workoutTitle),lower(?))>0 OR instr(strftime('%Y-%m-%d',startEpoch/1000,'unixepoch','localtime'),?)>0 OR instr(lower(machine),lower(?))>0"+
                (if(matching.isEmpty()) "" else " OR machine IN (${matching.joinToString { "?" }})")+")"
            val raw=q.search.trim()
            val date=raw.replace('/','-').replace('年','-').replace('月','-').removeSuffix("日")
            val parts=date.split('-')
            val normalized=if(parts.size in 2..3 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) parts.mapIndexed { i,v -> if(parts.size==3 && i==0) v else v.padStart(2,'0') }.joinToString("-") else date
            args.addAll(listOf(raw,raw,normalized,raw)); args.addAll(matching)
        }
        if(q.source!=0) { clauses+="demo=?"; args+=if(q.source==2) 1 else 0 }
        if(!includeArchive && q.archive!=2) { clauses+="archived=?"; args+=if(q.archive==1) 1 else 0 }
        return clauses.joinToString(" AND ") to args
    }
    suspend fun page(q: HistoryQuery, offset: Int=0, limit: Int=30): HistoryPage = withContext(Dispatchers.IO) {
        require(offset>=0 && limit in 1..100)
        val (where,args)=where(q)
        var result=HistoryPage(emptyList(),0)
        db.runInTransaction {
            val count=db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM sessions WHERE $where",args.toTypedArray())).use { it.moveToFirst(); it.getInt(0) }
            val rows=db.records().history(SimpleSQLiteQuery("SELECT * FROM sessions WHERE $where ORDER BY startEpoch DESC,id DESC LIMIT ? OFFSET ?",(args+listOf(limit,offset)).toTypedArray())).map { it.model() }
            result=HistoryPage(rows,count)
        }
        result
    }
    suspend fun overview(q: HistoryQuery,zone: ZoneId=ZoneId.systemDefault()): HistoryOverview = withContext(Dispatchers.IO) {
        val (where,args)=where(q,true)
        var result=HistoryOverview()
        // Read metadata only. Never load sample time series into the overview or list.
        val days=sortedMapOf<LocalDate,Long>(); var count=0; var demos=0; var present=0; var elapsed=0L; var kcal: Double?=null; var meters: Double?=null; var estimated=false
        db.query(SimpleSQLiteQuery("SELECT startEpoch,elapsedMs,caloriesKcal,distanceM,caloriesEstimated,demo FROM sessions WHERE $where",args.toTypedArray())).use { cursor ->
            while(cursor.moveToNext()) {
                count++; if(cursor.getInt(5)!=0) demos++; val ms=cursor.getLong(1); elapsed+=ms
                val date=Instant.ofEpochMilli(cursor.getLong(0)).atZone(zone).toLocalDate()
                days[date]=(days[date] ?: 0L)+ms
                if(!cursor.isNull(2)) { present++; kcal=(kcal ?: 0.0)+cursor.getDouble(2) }
                if(!cursor.isNull(3)) meters=(meters ?: 0.0)+cursor.getDouble(3)
                estimated=estimated || cursor.getInt(4)!=0
            }
        }
        result=HistoryOverview(count,elapsed,kcal,meters,estimated,days.toList(),present,demos); result
    }
    suspend fun reportIds(q: HistoryQuery): List<String> = withContext(Dispatchers.IO) {
        val (where,args)=where(q)
        db.query(SimpleSQLiteQuery("SELECT id FROM sessions WHERE $where ORDER BY startEpoch DESC,id DESC",args.toTypedArray())).use { cursor -> buildList { while(cursor.moveToNext()) add(cursor.getString(0)) } }
    }
    suspend fun session(id: String): Session? = withContext(Dispatchers.IO) { db.records().session(id)?.model() }
    suspend fun detail(id: String): Pair<Session,SessionStats>? = withContext(Dispatchers.IO) {
        var result: Pair<Session,SessionStats>?=null
        db.runInTransaction { db.records().session(id)?.let { result=it.model() to Statistics.summarize(db.records().samplesFor(id).map { row -> row.model().let { sample ->
            if(it.protocol in setOf("V1","V2") && sample.metrics.speedMps==null) sample.copy(metrics=sample.metrics.copy(speedMps=Estimates.legacySpeed(sample.metrics.cadence,Machine.valueOf(it.machine)))) else sample
        } }) } }
        result
    }
    fun archiveMatching(q: HistoryQuery): Int {
        val (where,args)=where(q.copy(archive=0))
        val stmt=db.openHelper.writableDatabase.compileStatement("UPDATE sessions SET archived=1 WHERE $where")
        return stmt.use { args.forEachIndexed { i,a -> if(a is Number) it.bindLong(i+1,a.toLong()) else it.bindString(i+1,a.toString()) }; it.executeUpdateDelete() }
    }
}
