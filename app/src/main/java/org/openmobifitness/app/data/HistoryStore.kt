package org.openmobifitness.app.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openmobifitness.core.*
import java.time.*

/** Legacy archive flags remain backup-compatible; current UI selects all flags by date. */
data class HistoryQuery(val from: Long=0, val until: Long=Long.MAX_VALUE, val machine: String="", val search: String="", val source: Int=0, val archive: Int=2, val machineWords: String="", val owner: String="all") {
    fun encode()=JSONObject().put("from",from).put("until",until).put("machine",machine).put("search",search).put("source",source).put("archive",archive).put("machineWords",machineWords).put("owner",owner).toString()
    fun frozen(users: List<UserProfile>): HistoryQuery=when(owner) {
        "all","removed" -> copy(owner="ids:"+users.filter { owner=="all" || !it.available }.joinToString(",") { it.id }+if(owner=="all") "|unassigned" else "")
        else -> this
    }
    companion object { fun decode(text: String?)=runCatching { JSONObject(text!!).let { HistoryQuery(it.getLong("from"),it.getLong("until"),it.getString("machine"),it.getString("search"),it.getInt("source"),it.getInt("archive"),it.optString("machineWords",""),it.optString("owner","all")) } }.getOrDefault(HistoryQuery()) }
}
data class HistoryPage(val rows: List<Session>, val count: Int)
data class HistoryOverview(val count: Int=0, val elapsedMs: Long=0, val calories: Double?=null, val distanceM: Double?=null, val estimated: Boolean=false, val days: List<Pair<LocalDate,Long>> = emptyList(), val caloriesPresent: Int=0, val demoCount: Int=0, val distanceEstimated: Boolean=false, val distancePresent: Int=0)
class HistoryStore(private val db: MobiDatabase) {
    private fun where(q: HistoryQuery, includeArchive: Boolean=false): Pair<String,List<Any>> {
        val clauses=mutableListOf("status!='active'","startEpoch>=?","startEpoch<?")
        val args=mutableListOf<Any>(q.from,q.until)
        when(q.owner) {
            "all" -> Unit
            "unassigned" -> clauses+="ownerUserId IS NULL"
            "removed" -> clauses+="ownerUserId IN (SELECT id FROM users WHERE removedAt IS NOT NULL)"
            "none" -> clauses+="0"
            else -> if(q.owner.startsWith("ids:")) {
                val parts=q.owner.removePrefix("ids:").split('|'); require(parts.size<=2 && (parts.size==1 || parts[1]=="unassigned"))
                val ids=parts[0].split(',').filter { it.isNotEmpty() }
                require(ids.size<=20000 && ids.all { runCatching { java.util.UUID.fromString(it).toString()==it }.getOrDefault(false) })
                val members=if(ids.isEmpty()) "0" else "ownerUserId IN (${ids.joinToString { "'$it'" }})"
                clauses+="("+members+if(parts.size==2) " OR ownerUserId IS NULL)" else ")"
            } else { require(runCatching { java.util.UUID.fromString(q.owner).toString()==q.owner }.getOrDefault(false)); clauses+="ownerUserId=?"; args+=q.owner }
        }
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
        val days=sortedMapOf<LocalDate,Long>(); var count=0; var demos=0; var present=0; var elapsed=0L; var kcal: Double?=null; var meters: Double?=null; var estimated=false; var distanceEstimated=false; var distancePresent=0
        db.query(SimpleSQLiteQuery("SELECT startEpoch,elapsedMs,caloriesKcal,distanceM,caloriesEstimated,demo,distanceEstimated FROM sessions WHERE $where",args.toTypedArray())).use { cursor ->
            while(cursor.moveToNext()) {
                count++; if(cursor.getInt(5)!=0) demos++; val ms=cursor.getLong(1); elapsed+=ms
                val date=Instant.ofEpochMilli(cursor.getLong(0)).atZone(zone).toLocalDate()
                days[date]=(days[date] ?: 0L)+ms
                if(!cursor.isNull(2)) { present++; kcal=(kcal ?: 0.0)+cursor.getDouble(2) }
                if(!cursor.isNull(3)) { distancePresent++; meters=(meters ?: 0.0)+cursor.getDouble(3) }
                estimated=estimated || cursor.getInt(4)!=0; distanceEstimated=distanceEstimated || cursor.getInt(6)!=0
            }
        }
        result=HistoryOverview(count,elapsed,kcal,meters,estimated,days.toList(),present,demos,distanceEstimated,distancePresent); result
    }
    suspend fun years(): List<Int> = withContext(Dispatchers.IO) {
        db.query(SimpleSQLiteQuery("SELECT DISTINCT strftime('%Y',startEpoch/1000,'unixepoch','localtime') FROM sessions WHERE status!='active' ORDER BY 1 DESC")).use { cursor ->
            buildList { add(LocalDate.now().year); while(cursor.moveToNext()) cursor.getString(0)?.toIntOrNull()?.let(::add) }.distinct().sortedDescending()
        }
    }
    suspend fun reportIds(q: HistoryQuery): List<String> = withContext(Dispatchers.IO) {
        val (where,args)=where(q)
        db.query(SimpleSQLiteQuery("SELECT id FROM sessions WHERE $where ORDER BY startEpoch DESC,id DESC",args.toTypedArray())).use { cursor -> buildList { while(cursor.moveToNext()) add(cursor.getString(0)) } }
    }
    suspend fun ownerName(id: String?): String?=withContext(Dispatchers.IO) { id?.let { db.records().user(it)?.name } }
    suspend fun session(id: String): Session? = withContext(Dispatchers.IO) { db.records().session(id)?.model() }
    suspend fun detail(id: String): Pair<Session,SessionStats>? = withContext(Dispatchers.IO) {
        var result: Pair<Session,SessionStats>?=null
        db.runInTransaction { db.records().session(id)?.let { row ->
            val stats=Statistics.Accumulator(); var after=-1L
            while(true) {
                val page=db.records().samplesForPage(id,after,1000); if(page.isEmpty()) break
                page.forEach { entry ->
                    val sample=entry.model()
                    val legacy=if(row.protocol in setOf("V1","V2","HUANTONG") && sample.metrics.speedMps==null) Estimates.legacySpeed(sample.metrics.cadence,Machine.valueOf(row.machine)) else null
                    stats.add(if(legacy!=null) sample.copy(metrics=sample.metrics.copy(speedMps=legacy)) else sample,legacy!=null)
                }
                after=page.last().elapsedMs
            }
            result=row.model() to stats.finish()
        } }
        result
    }
    fun archiveMatching(q: HistoryQuery): Int {
        val (where,args)=where(q.copy(archive=0))
        val stmt=db.openHelper.writableDatabase.compileStatement("UPDATE sessions SET archived=1 WHERE $where")
        return stmt.use { args.forEachIndexed { i,a -> if(a is Number) it.bindLong(i+1,a.toLong()) else it.bindString(i+1,a.toString()) }; it.executeUpdateDelete() }
    }
}

/** Six calendar months including the current month. Queries use an exclusive upper bound. */
object RecordDates {
    fun today(today: LocalDate=LocalDate.now(),zone: ZoneId=ZoneId.systemDefault()): Pair<Long,Long> =
        today.atStartOfDay(zone).toInstant().toEpochMilli() to today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun recent(today: LocalDate=LocalDate.now(),zone: ZoneId=ZoneId.systemDefault()): Pair<Long,Long> =
        today.withDayOfMonth(1).minusMonths(5).atStartOfDay(zone).toInstant().toEpochMilli() to today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun selected(year: Int,month: Int,zone: ZoneId=ZoneId.systemDefault()): Pair<Long,Long> {
        require(month in 0..12)
        if(year==0) return 0L to Long.MAX_VALUE
        val start=LocalDate.of(year,month.coerceAtLeast(1),1)
        val end=if(month==0) start.plusYears(1) else start.plusMonths(1)
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
