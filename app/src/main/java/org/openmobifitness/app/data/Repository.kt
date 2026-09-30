package org.openmobifitness.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.openmobifitness.core.*
import java.time.Instant

class Repository(context: Context) {
    val db = Room.databaseBuilder(context,MobiDatabase::class.java,"openmobi.db").build()
    private val dao get() = db.records()
    val sessions = MutableStateFlow<List<Session>>(emptyList())
    val workouts = MutableStateFlow<List<Workout>>(emptyList())
    suspend fun load() = withContext(Dispatchers.IO) {
        db.runInTransaction {
            dao.sessions().filter { it.status=="active" }.forEach { it.status="interrupted"; it.end=Instant.now().toString(); dao.session(it) }
        }
        refresh()
    }
    private fun refresh() {
        sessions.value=dao.sessions().map { it.model() }
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
        refresh()
    }
    suspend fun saveWorkout(workout: Workout) = withContext(Dispatchers.IO) {
        dao.workout(WorkoutRow().apply { id=workout.id; csv=Exchange.workouts(listOf(workout.copy(builtin=false))) }); refresh()
    }
    suspend fun deleteWorkout(id: String) = withContext(Dispatchers.IO) { dao.deleteWorkout(id); refresh() }
    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) { require(dao.session(id)?.status!="active"); dao.deleteSession(id); refresh() }
    suspend fun archive(sessionId: String?=null): Archive = withContext(Dispatchers.IO) {
        var archive=Archive()
        db.runInTransaction {
            archive=if(sessionId==null) Archive(dao.sessions().map { it.model() },dao.samples().map { it.model() },dao.workouts().map { Exchange.parse(it.csv).workouts.single() })
            else Archive(listOfNotNull(dao.session(sessionId)?.model()),dao.samplesFor(sessionId).map { it.model() })
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
                require(original==null || original==s || original==s.copy(status="interrupted")) { "session_conflict" }
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
private fun Session.row() = SessionRow().also {
    it.id=id; it.start=start; it.end=end; it.zone=zone; it.device=device; it.machine=machine.name; it.protocol=protocol.name
    it.elapsedMs=elapsedMs; it.distanceM=distanceM; it.demo=demo; it.status=status
}
private fun SessionRow.model() = Session(id,start,end,zone,device,Machine.valueOf(machine),Protocol.valueOf(protocol),elapsedMs,distanceM,demo,status)
private fun Sample.row() = SampleRow().also { s ->
    s.sessionId=sessionId; s.elapsedMs=elapsedMs
    metrics.let { s.cadence=it.cadence; s.resistance=it.resistance; s.speedMps=it.speedMps; s.distanceM=it.distanceM; s.heartBpm=it.heartBpm; s.powerW=it.powerW; s.strokes=it.strokes }
}
private fun SampleRow.model() = Sample(sessionId,elapsedMs,Metrics(cadence,resistance,speedMps,distanceM,heartBpm,powerW,strokes))
