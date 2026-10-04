package org.openmobifitness.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.openmobifitness.core.*
import java.time.Instant

class Repository(val context: Context) {
    val preferences=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    val profilePreferences=ProfilePreferences(context,preferences)
    val avatars=AvatarStore(context)
    companion object {
        val MIGRATION_7_8=object: androidx.room.migration.Migration(7,8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS users (id TEXT NOT NULL, name TEXT NOT NULL, weightKg REAL, met REAL NOT NULL DEFAULT 5.0, metSource TEXT NOT NULL DEFAULT 'default', avatar TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0, removedAt INTEGER, deleted INTEGER NOT NULL DEFAULT 0, pendingPreferences TEXT NOT NULL DEFAULT '', legacyHints TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))")
                db.execSQL("INSERT OR IGNORE INTO users(id,name) VALUES(?,?)",arrayOf(LEGACY_USER_ID,"Legacy user"))
                listOf("ownerUserId","startedUserId").forEach { db.execSQL("ALTER TABLE sessions ADD COLUMN $it TEXT DEFAULT NULL REFERENCES users(id) ON DELETE RESTRICT") }
                listOf("startedUserName","weightSource","metSource","workoutSnapshot","capabilitySnapshot","ownerHistory","enteredStageIds").forEach { db.execSQL("ALTER TABLE sessions ADD COLUMN $it TEXT NOT NULL DEFAULT ''") }
                db.execSQL("ALTER TABLE sessions ADD COLUMN identityVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX index_sessions_ownerUserId_startEpoch ON sessions(ownerUserId,startEpoch)")
                db.execSQL("CREATE INDEX index_sessions_startedUserId ON sessions(startedUserId)")
                db.execSQL("ALTER TABLE workouts ADD COLUMN ownerUserId TEXT DEFAULT NULL REFERENCES users(id) ON DELETE RESTRICT")
                db.execSQL("CREATE INDEX index_workouts_ownerUserId ON workouts(ownerUserId)")
                db.execSQL("UPDATE workouts SET ownerUserId=?",arrayOf(LEGACY_USER_ID))
            }
        }
        val MIGRATION_6_7=object: androidx.room.migration.Migration(6,7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS preference_restore (id INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_5_6=object: androidx.room.migration.Migration(5,6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                listOf("jumpCount","continuousJumps","jumpInterruptions","repetitions","deviceDurationSec","dumbbellFewActions","dumbbellActionNumber").forEach { db.execSQL("ALTER TABLE samples ADD COLUMN $it INTEGER") }
                db.execSQL("ALTER TABLE samples ADD COLUMN loadKg REAL")
            }
        }
        val MIGRATION_4_5=object: androidx.room.migration.Migration(4,5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN energyModel TEXT NOT NULL DEFAULT ''")
            }
        }
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
    val db = Room.databaseBuilder(context,MobiDatabase::class.java,"openmobi.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8).build()
    private val dao get() = db.records()
    val maintenance=MutableStateFlow(false)
    val revision = MutableStateFlow(0L)
    val history by lazy { HistoryStore(db) }
    val sessions = MutableStateFlow<List<Session>>(emptyList())
    val workouts = MutableStateFlow<List<Workout>>(emptyList())
    val users = MutableStateFlow<List<UserProfile>>(emptyList())
    suspend fun load() = withContext(Dispatchers.IO) {
        recoverPreferences()
        migrateLegacyProfile()
        recoverUserPreferences()
        recoverAvatarCleanup()
        db.runInTransaction {
            dao.activeSessions().forEach { it.status="interrupted"; it.end=Instant.now().toString(); dao.session(it) }
        }
        refresh()
    }
    internal fun refresh() {
        revision.update { it+1 }
        sessions.value=dao.recentSessions().map { it.model() }
        workouts.value=dao.workouts().map { it.model() }
        users.value=dao.users().map { it.model() }
    }
    internal fun recoverPreferences() {
        dao.pendingPreferences()?.let { pending ->
            check(PortablePreferences.apply(preferences,pending.json)) { "preference_restore_pending" }
            dao.clearPendingPreferences()
        }
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
        val owner=workout.ownerUserId ?: profilePreferences.userId
        require(owner!=null && dao.user(owner)?.model()?.available==true) { "user_required" }
        require(profilePreferences.userId==owner) { "workout_owner_conflict" }
        val existing=dao.workoutAt(workout.id)
        require(existing==null || existing.ownerUserId==owner) { "workout_owner_conflict" }
        val saved=workout.copy(builtin=false,ownerUserId=owner)
        if(!saved.legacyUnclassified) { require(saved.machine!=null) { "workout_machine_required" }; WorkoutPolicy.validate(saved) }
        dao.workout(saved.row()); refresh()
    }
    suspend fun deleteWorkout(id: String,ownerId: String?=profilePreferences.userId) = withContext(Dispatchers.IO) {
        db.runInTransaction { require(ownerId!=null && dao.workoutAt(id)?.ownerUserId==ownerId) { "workout_owner_conflict" }; dao.deleteWorkout(id) }; refresh()
    }
    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) { require(dao.session(id)?.status!="active"); dao.deleteSession(id); refresh() }
    suspend fun archive(sessionId: String?=null,includeSamples: Boolean=true,includeWorkouts: Boolean=true,includeSessions: Boolean=true): Archive = withContext(Dispatchers.IO) {
        var archive=Archive()
        db.runInTransaction {
            archive=if(sessionId==null) Archive(if(includeSessions) dao.sessions().map { it.model() } else emptyList(),if(includeSamples) dao.samples().map { it.model() } else emptyList(),if(includeWorkouts) dao.workouts().map { it.model() } else emptyList(),dao.users().map(::exportUser))
            else Archive(if(includeSessions) listOfNotNull(dao.session(sessionId)?.model()) else emptyList(),if(includeSamples) dao.samplesFor(sessionId).map { it.model() } else emptyList())
        }
        archive
    }
    suspend fun importArchive(incoming: Archive): Int = withContext(Dispatchers.IO) {
        var count=0
        db.runInTransaction {
            incoming.users.forEach { user -> if(dao.user(user.id)==null) { require(user.avatar.isEmpty() || avatars.file(user.avatar).isFile); dao.user(user.row()); count++ } }
            val existing=dao.sessions().map { it.model() }.associateBy { it.id }
            Exchange.validateReferences(incoming,existing.values.toList())
            incoming.sessions.forEach { s ->
                val original=existing[s.id]
                require(original==null || BackupTransfer.compatible(original,s)) { "session_conflict" }
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
                val owned=if(w.ownerUserId==null) {
                    if(dao.user(LEGACY_USER_ID)==null) dao.user(UserProfile(LEGACY_USER_ID,"Legacy user",legacyHints="{\"version\":1}").row())
                    w.copy(ownerUserId=LEGACY_USER_ID,machine=if(w.steps.any { it.condition==Condition.STROKES }) Machine.ROWER else null,legacyUnclassified=w.steps.none { it.condition==Condition.STROKES })
                } else w
                val old=workouts[w.id]
                require(old==null || old.model()==owned) { "workout_conflict" }
                if(old==null) { dao.workout(owned.row()); count++ }
            }
        }
        recoverUserPreferences(); refresh(); count
    }
}
internal fun Session.row() = SessionRow().also {
    it.id=id; it.start=start; it.startEpoch=Instant.parse(start).toEpochMilli(); it.archived=archived; it.workoutId=workoutId; it.workoutTitle=workoutTitle; it.end=end; it.zone=zone; it.device=device; it.machine=machine.name; it.protocol=protocol.name
    it.elapsedMs=elapsedMs; it.distanceM=distanceM; it.demo=demo; it.status=status
    it.caloriesKcal=caloriesKcal; it.caloriesEstimated=caloriesEstimated; it.distanceEstimated=distanceEstimated; it.weightKg=weightKg; it.met=met; it.energyModel=energyModel
    it.ownerUserId=ownerUserId; it.startedUserId=startedUserId; it.startedUserName=startedUserName; it.weightSource=weightSource; it.metSource=metSource; it.workoutSnapshot=workoutSnapshot; it.capabilitySnapshot=capabilitySnapshot; it.identityVersion=identityVersion; it.ownerHistory=ownerHistory; it.enteredStageIds=enteredStageIds
}
internal fun SessionRow.model() = Session(id,start,end,zone,device,Machine.valueOf(machine),Protocol.valueOf(protocol),elapsedMs,distanceM,demo,status,caloriesKcal,caloriesEstimated,distanceEstimated,weightKg,met,workoutId,workoutTitle,archived,energyModel,ownerUserId,startedUserId,startedUserName,weightSource,metSource,workoutSnapshot,capabilitySnapshot,identityVersion,ownerHistory,enteredStageIds)
internal fun Sample.row() = SampleRow().also { s ->
    s.sessionId=sessionId; s.elapsedMs=elapsedMs
    metrics.let { s.cadence=it.cadence; s.resistance=it.resistance; s.speedMps=it.speedMps; s.distanceM=it.distanceM; s.heartBpm=it.heartBpm; s.powerW=it.powerW; s.strokes=it.strokes
        s.caloriesKcal=it.caloriesKcal; s.inclinePercent=it.inclinePercent; s.strideM=it.strideM; s.forceN=it.forceN; s.stepRate=it.stepRate; s.stepCount=it.stepCount; s.targetCadence=it.targetCadence; s.powerEstimated=it.powerEstimated
        s.jumpCount=it.jumpCount; s.continuousJumps=it.continuousJumps; s.jumpInterruptions=it.jumpInterruptions; s.repetitions=it.repetitions; s.loadKg=it.loadKg; s.deviceDurationSec=it.deviceDurationSec; s.dumbbellFewActions=it.dumbbellFewActions; s.dumbbellActionNumber=it.dumbbellActionNumber }
}
internal fun SampleRow.model() = Sample(sessionId,elapsedMs,Metrics(cadence,resistance,speedMps,distanceM,heartBpm,powerW,strokes,caloriesKcal,inclinePercent,strideM,forceN,stepRate,stepCount,targetCadence,powerEstimated,jumpCount,continuousJumps,jumpInterruptions,repetitions,loadKg,deviceDurationSec,dumbbellFewActions,dumbbellActionNumber))
