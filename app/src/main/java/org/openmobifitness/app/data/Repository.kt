package org.openmobifitness.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.openmobifitness.core.*
import java.time.Instant

class Repository(context: Context) {
    companion object {
        val MIGRATION_3_4=object: androidx.room.migration.Migration(3,4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN startEpoch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sessions SET startEpoch=CAST(strftime('%s',start) AS INTEGER)*1000")
                db.execSQL("ALTER TABLE sessions ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                listOf("workoutId","workoutTitle").forEach { db.execSQL("ALTER TABLE sessions ADD COLUMN $it TEXT NOT NULL DEFAULT ''") }
                db.execSQL("CREATE INDEX index_sessions_startEpoch ON sessions(startEpoch)")
                db.execSQL("CREATE INDEX index_sessions_archived_startEpoch ON sessions(archived,startEpoch)")
                db.execSQL("CREATE INDEX index_sessions_machine_startEpoch ON sessions(machine,startEpoch)")
            }
        }
        val MIGRATION_2_3=object: androidx.room.migration.Migration(2,3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE samples ADD COLUMN powerEstimated INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_1_2=object: androidx.room.migration.Migration(1,2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                listOf("caloriesKcal","weightKg","met").forEach { db.execSQL("ALTER TABLE sessions ADD COLUMN $it REAL") }
                listOf("caloriesEstimated","distanceEstimated").forEach { db.execSQL("ALTER TABLE sessions ADD COLUMN $it INTEGER NOT NULL DEFAULT 0") }
                listOf("caloriesKcal","inclinePercent","strideM","forceN","stepRate","targetCadence").forEach { db.execSQL("ALTER TABLE samples ADD COLUMN $it REAL") }
                db.execSQL("ALTER TABLE samples ADD COLUMN stepCount INTEGER")
            }
        }
    }
    val db = Room.databaseBuilder(context,MobiDatabase::class.java,"openmobi.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
    private val dao get() = db.records()
    val revision = MutableStateFlow(0L)
    val history by lazy { HistoryStore(db) }
    val sessions = MutableStateFlow<List<Session>>(emptyList())
    val workouts = MutableStateFlow<List<Workout>>(emptyList())
    suspend fun load() = withContext(Dispatchers.IO) {
        db.runInTransaction {
            dao.activeSessions().forEach { it.status="interrupted"; it.end=Instant.now().toString(); dao.session(it) }
        }
        refresh()
    }
    private fun refresh() {
        revision.update { it+1 }
        sessions.value=dao.recentSessions().map { it.model() }
        workouts.value=dao.workouts().map { Exchange.parse(it.csv).workouts.single() }
    }
    suspend fun save(session: Session, sample: Sample? = null) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            // A delayed checkpoint must not resurrect an already finished session.
            val stored=dao.session(session.id)
            if(stored == null || stored.status=="active" || session.status!="active") {
                dao.session(session.row())
                if(sample!=null) dao.sample(sample.row())
            }
        }
        // Live samples do not reload/sort the entire library every second.
        if(sample==null || session.status!="active") refresh()
    }
    suspend fun setArchived(id: String, value: Boolean) = withContext(Dispatchers.IO) { dao.setArchived(id,value); refresh() }
    suspend fun archiveMatching(query: HistoryQuery): Int = withContext(Dispatchers.IO) { val count=history.archiveMatching(query); refresh(); count }
    suspend fun validate(incoming: Archive) = withContext(Dispatchers.IO) { Exchange.validateReferences(incoming,dao.sessions().map { it.model() }) }
    suspend fun saveWorkout(workout: Workout) = withContext(Dispatchers.IO) {
        dao.workout(WorkoutRow().apply { id=workout.id; csv=Exchange.workouts(listOf(workout.copy(builtin=false))) }); refresh()
    }
    suspend fun deleteWorkout(id: String) = withContext(Dispatchers.IO) { dao.deleteWorkout(id); refresh() }
    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) { require(dao.session(id)?.status!="active"); dao.deleteSession(id); refresh() }
    suspend fun archive(sessionId: String?=null,includeSamples: Boolean=true,includeWorkouts: Boolean=true,includeSessions: Boolean=true): Archive = withContext(Dispatchers.IO) {
        var archive=Archive()
        db.runInTransaction {
            archive=if(sessionId==null) Archive(if(includeSessions) dao.sessions().map { it.model() } else emptyList(),if(includeSamples) dao.samples().map { it.model() } else emptyList(),if(includeWorkouts) dao.workouts().map { Exchange.parse(it.csv).workouts.single() } else emptyList())
            else Archive(if(includeSessions) listOfNotNull(dao.session(sessionId)?.model()) else emptyList(),if(includeSamples) dao.samplesFor(sessionId).map { it.model() } else emptyList())
        }
        archive
    }
    suspend fun importArchive(incoming: Archive): Int = withContext(Dispatchers.IO) {
        var count=0
        db.runInTransaction {
            val existing=dao.sessions().map { it.model() }.associateBy { it.id }
            Exchange.validateReferences(incoming,existing.values.toList())
            incoming.sessions.forEach { s ->
                val original=existing[s.id]
                require(original==null || original.copy(archived=false)==s.copy(archived=false) || original.copy(archived=false)==s.copy(status="interrupted",archived=false)) { "session_conflict" }
                require(original?.status!="active") { "active_session" }
                if(original==null) { dao.session(s.copy(status=if(s.status=="active") "interrupted" else s.status).row()); count++ }
            }
            val samples=dao.samples().map { it.model() }.associateBy { it.sessionId to it.elapsedMs }
            incoming.samples.forEach { s ->
                val original=samples[s.sessionId to s.elapsedMs]; require(original==null || original==s) { "sample_conflict" }
                if(original==null) { dao.sample(s.row()); count++ }
            }
            val workouts=dao.workouts().associateBy { it.id }
            incoming.workouts.forEach { w ->
                val csv=Exchange.workouts(listOf(w)); val old=workouts[w.id]
                require(old==null || Exchange.parse(old.csv).workouts.single()==w) { "workout_conflict" }
                if(old==null) { dao.workout(WorkoutRow().apply { id=w.id; this.csv=csv }); count++ }
            }
        }
        refresh(); count
    }
}
internal fun Session.row() = SessionRow().also {
    it.id=id; it.start=start; it.startEpoch=Instant.parse(start).toEpochMilli(); it.archived=archived; it.workoutId=workoutId; it.workoutTitle=workoutTitle; it.end=end; it.zone=zone; it.device=device; it.machine=machine.name; it.protocol=protocol.name
    it.elapsedMs=elapsedMs; it.distanceM=distanceM; it.demo=demo; it.status=status
    it.caloriesKcal=caloriesKcal; it.caloriesEstimated=caloriesEstimated; it.distanceEstimated=distanceEstimated; it.weightKg=weightKg; it.met=met
}
internal fun SessionRow.model() = Session(id,start,end,zone,device,Machine.valueOf(machine),Protocol.valueOf(protocol),elapsedMs,distanceM,demo,status,caloriesKcal,caloriesEstimated,distanceEstimated,weightKg,met,workoutId,workoutTitle,archived)
private fun Sample.row() = SampleRow().also { s ->
    s.sessionId=sessionId; s.elapsedMs=elapsedMs
    metrics.let { s.cadence=it.cadence; s.resistance=it.resistance; s.speedMps=it.speedMps; s.distanceM=it.distanceM; s.heartBpm=it.heartBpm; s.powerW=it.powerW; s.strokes=it.strokes
        s.caloriesKcal=it.caloriesKcal; s.inclinePercent=it.inclinePercent; s.strideM=it.strideM; s.forceN=it.forceN; s.stepRate=it.stepRate; s.stepCount=it.stepCount; s.targetCadence=it.targetCadence; s.powerEstimated=it.powerEstimated }
}
internal fun SampleRow.model() = Sample(sessionId,elapsedMs,Metrics(cadence,resistance,speedMps,distanceM,heartBpm,powerW,strokes,caloriesKcal,inclinePercent,strideM,forceN,stepRate,stepCount,targetCadence,powerEstimated))
