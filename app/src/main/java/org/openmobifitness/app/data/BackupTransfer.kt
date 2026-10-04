package org.openmobifitness.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.*
import org.openmobifitness.core.*
import java.io.*
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.*
import org.json.JSONObject
import org.json.JSONArray

data class RestorePreview(val sessions: Long,val samples: Long,val workouts: Long,val added: Long,val duplicates: Long,val conflicts: Long,val users: Long=0,val removedUsers: Long=0)
class StagedBackup internal constructor(val directory: File,val db: MobiDatabase,val preferences: String?,val preview: RestorePreview,val avatars: Map<String,File> = emptyMap()): Closeable {
    override fun close() { db.close(); directory.deleteRecursively() }
}
/** ZIP v8: users, content-addressed avatars and independently parseable CSV chunks.
 * Memory is bounded by a chunk, SQLite cursors and a single CSV row, not archive size. */
object BackupTransfer {
    const val MAX_BYTES=2L*1024*1024*1024
    const val MAX_ENTRIES=20000
    private const val PAGE=2000
    suspend fun export(repo: Repository,output: OutputStream,preferences: String?,progress: (Long)->Unit={})=withContext(Dispatchers.IO) {
        val job=currentCoroutineContext()[Job]!!; var bytes=0L; var entries=0
        ZipOutputStream(output).use { zip ->
            fun binary(name: String,data: ByteArray) {
                job.ensureActive(); bytes+=data.size
                require(bytes<=MAX_BYTES && ++entries<=MAX_ENTRIES) { "backup_capacity" }
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry(); progress(bytes)
            }
            fun entry(name: String,value: String) { if(name.endsWith(".csv")) Exchange.parse(value); binary(name,value.toByteArray(Charsets.UTF_8)) }
            repo.db.runInTransaction {
                entry("manifest.txt","OpenMOBI backup 8\n")
                val users=repo.db.records().users()
                if(users.isEmpty()) entry("users/0.csv",Exchange.users(emptyList()))
                users.chunked(8).forEachIndexed { i,rows -> entry("users/$i.csv",Exchange.users(rows.map(repo::exportUser))) }
                users.map { it.avatar }.filter { it.isNotEmpty() }.distinct().forEach { reference -> val bytes=repo.avatars.file(reference).readBytes(); repo.avatars.verify(reference,bytes); binary("avatars/$reference",bytes) }
                preferences?.let { PortablePreferences.validate(it); entry("preferences.json",it) }
                var after=""; var part=0
                do { val page=repo.db.records().sessionPage(after,32); entry("sessions/${part++}.csv",Exchange.sessions(page.map { it.model() })); if(page.isEmpty()) break; after=page.last().id } while(true)
                after=""; var time=-1L; part=0
                do { val page=repo.db.records().samplePage(after,time,PAGE); entry("samples/${part++}.csv",Exchange.samples(page.map { it.model() })); if(page.isEmpty()) break; after=page.last().sessionId; time=page.last().elapsedMs } while(true)
                after=""; part=0
                do {
                    val page=repo.db.records().workoutPage(after,100)
                    if(page.isEmpty()) { if(part==0) entry("workouts/0.csv",Exchange.workouts(emptyList())); break }
                    entry("workouts/${part++}.csv",Exchange.workouts(page.map { it.model() })); after=page.last().id
                } while(true)
            }
        }
    }
    suspend fun stage(context: Context,repo: Repository,input: InputStream,progress: (Long)->Unit={}): StagedBackup {
        var completed: StagedBackup?=null
        try { return withContext(Dispatchers.IO) {
        val job=currentCoroutineContext()[Job]!!
        val directory=File(context.cacheDir,"restore-${UUID.randomUUID()}").apply { check(mkdirs()) }
        var staged: MobiDatabase?=null
        try {
            val parts=linkedMapOf<String,File>(); var bytes=0L
            fun copy(name: String,source: InputStream) {
                require(parts.size<MAX_ENTRIES && !parts.containsKey(name)) { "backup_entries" }
                val file=File(directory,"part-${parts.size}"); parts[name]=file
                file.outputStream().buffered().use { out -> val buffer=ByteArray(64*1024); while(true) {
                    job.ensureActive(); val n=source.read(buffer); if(n<0) break; bytes+=n
                    require(bytes<=MAX_BYTES) { "backup_capacity" }; out.write(buffer,0,n); progress(bytes)
                } }
            }
            val buffered=PushbackInputStream(input.buffered(),4); val magic=ByteArray(4); val count=buffered.read(magic); if(count>0) buffered.unread(magic,0,count)
            val zip=count>=2 && magic[0]==0x50.toByte() && magic[1]==0x4b.toByte()
            if(zip) ZipInputStream(buffered).use { stream ->
                while(true) {
                    val entry=stream.nextEntry ?: break
                    require(!entry.isDirectory && (entry.name in setOf("manifest.txt","sessions.csv","samples.csv","workouts.csv","preferences.json") || entry.name.matches(Regex("(users|sessions|samples|workouts)/[0-9]{1,6}\\.csv")) || entry.name.matches(Regex("avatars/[a-f0-9]{64}\\.jpg")))) { "backup_entry" }
                    copy(entry.name,stream); stream.closeEntry()
                }
            } else copy("input.csv",buffered)
            if(zip) {
                val manifest=parts["manifest.txt"] ?: error("backup_manifest")
                require(manifest.length()<128)
                val text=manifest.readText()
                if(text in setOf("OpenMOBI backup 7\n","OpenMOBI backup 8\n")) {
                    require(parts.keys.none { it in setOf("sessions.csv","samples.csv","workouts.csv") })
                    (listOf("sessions","samples","workouts")+if(text=="OpenMOBI backup 8\n") listOf("users") else emptyList()).forEach { type ->
                        val numbers=parts.keys.filter { it.startsWith("$type/") }.map { it.substringAfter('/').substringBefore('.').toInt() }.sorted()
                        require(numbers.isNotEmpty() && numbers==numbers.indices.toList()) { "backup_chunks" }
                    }
                    if(text=="OpenMOBI backup 7\n") require(parts.keys.none { it.startsWith("users/") || it.startsWith("avatars/") })
                } else { require(text in setOf("OpenMobi backup 1\n","OpenMobi backup 2\n","OpenMOBI backup 3\n","OpenMOBI backup 4\n","OpenMOBI backup 5\n","OpenMOBI backup 6\n")); require(parts.keys==setOf("manifest.txt","sessions.csv","samples.csv","workouts.csv")) }
            }
            val prefs=parts["preferences.json"]?.let { require(it.length()<=1024*1024); it.readText().also(PortablePreferences::validate) }
            staged=Room.databaseBuilder(context,MobiDatabase::class.java,File(directory,"staged.db").absolutePath).build()
            val dao=staged.records()
            var parentCache: SessionRow?=null
            val ordered=parts.filterKeys { it.endsWith(".csv") }.entries.sortedWith(compareBy({ when { it.key.startsWith("users") -> 0; it.key.startsWith("sessions") -> 1; it.key.startsWith("samples") -> 3; else -> 2 } },{ it.key }))
            staged.runInTransaction {
                ordered.forEach { (name,file) ->
                    fun insert(archive: Archive) {
                        job.ensureActive()
                        fun userReference(id: String) {
                            if(dao.user(id)==null) {
                                // Standalone CSV can reference profiles already in this installation.
                                require(!zip || parts["manifest.txt"]?.readText()!="OpenMOBI backup 8\n") { "user_reference" }
                                val existing=repo.db.records().user(id) ?: error("user_reference")
                                existing.pendingPreferences=""; dao.user(existing)
                            }
                        }
                        archive.users.forEach { user ->
                            require(dao.user(user.id)==null) { "duplicate_user" }
                            SnapshotValidation.legacy(user.legacyHints)
                            if(user.preferences.isNotEmpty()) PortablePreferences.validate(user.preferences).getJSONObject("values").keys().forEach { require(ProfilePreferences.personal(it)) { "personal_preference_scope" } }
                            dao.user(user.row())
                        }
                        archive.sessions.forEach { s ->
                            require(dao.session(s.id)==null) { "duplicate_session" }
                            sessionUserIds(s).forEach(::userReference)
                            dao.session(s.row())
                        }
                        archive.workouts.forEach { w ->
                            require(dao.workoutAt(w.id)==null) { "duplicate_workout" }
                            val owned=if(w.ownerUserId==null) {
                                if(dao.user(LEGACY_USER_ID)==null) dao.user((repo.db.records().user(LEGACY_USER_ID)?.model() ?: UserProfile(LEGACY_USER_ID,"Legacy user",legacyHints="{\"version\":1}")).copy(preferences="").row())
                                w.copy(ownerUserId=LEGACY_USER_ID,machine=if(w.steps.any { it.condition==Condition.STROKES }) Machine.ROWER else null,legacyUnclassified=w.steps.none { it.condition==Condition.STROKES })
                            } else { userReference(w.ownerUserId!!); w }
                            dao.workout(owned.row())
                        }
                        val samples=archive.samples.map { s ->
                            job.ensureActive()
                            val parent=parentCache?.takeIf { it.id==s.sessionId } ?: (dao.session(s.sessionId) ?: repo.db.records().session(s.sessionId)?.also { sessionUserIds(it.model()).forEach(::userReference); dao.session(it) } ?: error("sample_reference")).also { parentCache=it }
                            require(s.elapsedMs<=parent.elapsedMs) { "sample_reference" }; s.row()
                        }
                        if(samples.isNotEmpty()) dao.samples(samples)
                    }
                    parseCsv(file,name,job,::insert)
                }
            }
            val avatarFiles=parts.filterKeys { it.startsWith("avatars/") }.mapKeys { it.key.substringAfter('/') }
            avatarFiles.forEach { (reference,file) -> require(file.length()<=2*1024*1024); repo.avatars.verify(reference,file.readBytes()) }
            val references=dao.users().map { it.avatar }.filter { it.isNotEmpty() }.toSet()
            require(avatarFiles.keys.all { it in references }) { "avatar_reference" }
            references.forEach { reference -> require(avatarFiles[reference]!=null || repo.avatars.file(reference).isFile) { "avatar_missing" } }
            val preview=inspect(repo.db,staged,job)
            StagedBackup(directory,staged,prefs,preview,avatarFiles).also { completed=it }
        } catch(e: Throwable) { staged?.close(); directory.deleteRecursively(); throw e }
        } } catch(e: Throwable) { withContext(NonCancellable+Dispatchers.IO) { completed?.close() }; throw e }
    }
    private fun parseCsv(file: File,name: String,job: Job,consume: (Archive)->Unit) {
        val reader=InputStreamReader(file.inputStream(),Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        reader.use {
            var header: List<String>?=null; val batch=mutableListOf<List<String>>(); var workout=false; var rows=0; var characters=0
            fun flush() { if(batch.isNotEmpty()) { consume(Exchange.parse(Csv.write(listOf(header!!)+batch))); batch.clear(); characters=0 } }
            Csv.stream(reader) { row ->
                job.ensureActive()
                if(header==null) {
                    Exchange.parse(Csv.write(listOf(row))); header=row; workout=row.getOrNull(1)=="workout_id"
                    if(name!="input.csv") require(when { name.startsWith("sessions") -> row.getOrNull(2)=="start_utc"; name.startsWith("samples") -> row.getOrNull(2)=="elapsed_ms"; name.startsWith("users") -> row.getOrNull(1)=="user_id"; else -> workout })
                } else { batch.add(row); characters+=row.sumOf { it.length }; rows++; if(workout) require(rows<=200000 && file.length()<=32*1024*1024) { "workout_capacity" }; if(!workout && (batch.size>=PAGE || characters>=4*1024*1024)) flush() }
            }
            require(header!=null); flush()
        }
    }
    internal fun compatible(old: Session,row: Session): Boolean {
        val noIdentity=row.identityVersion==0 && row.ownerUserId==null && row.startedUserId==null && row.ownerHistory.isEmpty() && row.startedUserName.isEmpty() && row.weightSource.isEmpty() && row.metSource.isEmpty() && row.workoutSnapshot.isEmpty() && row.capabilitySnapshot.isEmpty() && row.enteredStageIds.isEmpty()
        val comparable=if(noIdentity) old.copy(ownerUserId=null,startedUserId=null,startedUserName="",weightSource="",metSource="",workoutSnapshot="",capabilitySnapshot="",identityVersion=0,ownerHistory="",enteredStageIds="") else old
        return old.status!="active" && (comparable.copy(archived=false)==row.copy(archived=false) || comparable.copy(archived=false)==row.copy(status="interrupted",archived=false))
    }
    private fun sessionUserIds(session: Session): Set<String> = buildSet {
        SnapshotValidation.capability(session); addAll(SnapshotValidation.workout(session))
        session.ownerUserId?.let(::add); session.startedUserId?.let(::add)
        if(session.ownerHistory.isNotEmpty()) {
            val history=JSONArray(session.ownerHistory); require(history.length()<=1000)
            val changes=HashSet<String>()
            for(i in 0 until history.length()) {
                val item=history.getJSONObject(i); val id=item.getString("id"); UUID.fromString(id); require(changes.add(id)); java.time.Instant.parse(item.getString("at"))
                listOf("from","to").forEach { key -> if(!item.isNull(key)) add(item.getString(key).also { UUID.fromString(it) }) }
            }
        }
    }
    private fun workoutCompatible(old: WorkoutRow,row: WorkoutRow): Boolean {
        val original=old.model(); val incoming=row.model()
        return original==incoming || original.copy(steps=original.steps.map { it.copy(id="") })==incoming
    }
    /** Bounded merge cursor for samples visited in primary-key order. Imported
     * session IDs are UUID text, so this ordering matches SQLite BINARY. */
    private class SampleCursor(private val dao: WorkoutDao,private val job: Job) {
        private var page=emptyList<SampleRow>()
        private var index=0
        private var after=""
        private var time=-1L
        private var exhausted=false
        private fun peek(): SampleRow? {
            if(index==page.size && !exhausted) {
                job.ensureActive(); page=dao.samplePage(after,time,PAGE); index=0
                if(page.isEmpty()) exhausted=true else { after=page.last().sessionId; time=page.last().elapsedMs }
            }
            return page.getOrNull(index)
        }
        fun matching(row: SampleRow): SampleRow? {
            while(true) {
                val old=peek() ?: return null
                val order=old.sessionId.compareTo(row.sessionId).takeIf { it!=0 } ?: old.elapsedMs.compareTo(row.elapsedMs)
                if(order>0) return null
                index++
                if(order==0) return old
            }
        }
    }
    private fun pages(db: MobiDatabase,job: Job,session: (SessionRow)->Unit,sample: (SampleRow)->Unit,workout: (WorkoutRow)->Unit) {
        val dao=db.records(); var after=""; var time=-1L
        while(true) { job.ensureActive(); val page=dao.sessionPage(after,32); if(page.isEmpty()) break; page.forEach(session); after=page.last().id }
        after=""
        while(true) { job.ensureActive(); val page=dao.samplePage(after,time,PAGE); if(page.isEmpty()) break; page.forEach(sample); after=page.last().sessionId; time=page.last().elapsedMs }
        after=""
        while(true) { job.ensureActive(); val page=dao.workoutPage(after,100); if(page.isEmpty()) break; page.forEach(workout); after=page.last().id }
    }
    private fun inspect(base: MobiDatabase,source: MobiDatabase,job: Job): RestorePreview {
        var added=0L; var duplicates=0L; var conflicts=0L; val dao=base.records()
        val samples=SampleCursor(dao,job)
        pages(source,job,{ row ->
            val old=dao.session(row.id)
            when { old==null -> added++; compatible(old.model(),row.model()) -> duplicates++; else -> conflicts++ }
        },{ row -> val old=samples.matching(row); if(old!=null && old.model()!=row.model()) conflicts++ },{ row ->
            val old=dao.workoutAt(row.id)
            when { old==null -> added++; workoutCompatible(old,row) -> duplicates++; else -> conflicts++ }
        })
        return RestorePreview(source.records().sessionCount(),source.records().sampleCount(),source.records().workoutCount(),added,duplicates,conflicts,source.records().users().size.toLong(),source.records().users().count { it.removedAt!=null }.toLong())
    }
    suspend fun restore(repo: Repository,staged: StagedBackup,groups: Set<PreferenceGroup>,progress: (Long)->Unit={},finalizing: ()->Unit={},overwriteProfiles: Boolean=false): Long {
        var committed=false
        var added=0L
        try { return withContext(Dispatchers.IO) {
        val job=currentCoroutineContext()[Job]!!; val dao=repo.db.records(); var processed=0L
        require(staged.preview.conflicts==0L) { "restore_conflict" }
        // Content-addressed files are staged before the transaction. A failed merge may leave
        // an unreferenced image, but cannot replace any live profile image or lose an old one.
        staged.avatars.forEach { (reference,file) ->
            val destination=repo.avatars.file(reference)
            if(!destination.exists()) { val pending=File(destination.parentFile,"$reference.pending"); file.copyTo(pending,true); check(pending.renameTo(destination)) }
        }
        repo.db.runInTransaction {
            check(dao.activeSessions().isEmpty()) { "identity_locked" }
            staged.db.records().users().forEach { row ->
                job.ensureActive(); val old=dao.user(row.id)
                if(old==null) { dao.user(row); added++ }
                else if(overwriteProfiles && !old.deleted) {
                    if(old.avatar!=row.avatar) repo.avatars.queueCleanup(setOf(old.avatar))
                    row.removedAt=old.removedAt; row.deleted=old.deleted; dao.user(row)
                }
            }
            check(dao.pendingPreferences()==null) { "preference_restore_pending" }
            val existing=SampleCursor(dao,job)
            val pending=ArrayList<SampleRow>(PAGE)
            var parentCache: SessionRow?=null
            fun flush() { if(pending.isNotEmpty()) { job.ensureActive(); dao.samples(pending); pending.clear() } }
            pages(staged.db,job,{ row ->
                val old=dao.session(row.id)
                require(old==null || compatible(old.model(),row.model())) { "session_conflict" }
                if(old==null) { if(row.status=="active") row.status="interrupted"; dao.session(row); added++ }
                progress(++processed)
            },{ row ->
                val old=existing.matching(row); require(old==null || old.model()==row.model()) { "sample_conflict" }
                val parent=parentCache?.takeIf { it.id==row.sessionId } ?: dao.session(row.sessionId)?.also { parentCache=it }
                require(parent?.let { it.status!="active" && row.elapsedMs<=it.elapsedMs }==true) { "sample_reference" }
                if(old==null) { pending.add(row); added++ }; if(pending.size==PAGE) flush()
                if(++processed%PAGE==0L) progress(processed)
            },{ row ->
                val old=dao.workoutAt(row.id); require(old==null || workoutCompatible(old,row)) { "workout_conflict" }
                if(old==null) { dao.workout(row); added++ }; progress(++processed)
            })
            flush(); finalizing(); job.ensureActive()
            if(groups.isNotEmpty() && staged.preferences!=null) {
                val settings=PortablePreferences.validate(PortablePreferences.select(staged.preferences,groups)); val values=settings.getJSONObject("values")
                val personal=JSONObject(); values.keys().asSequence().toList().filter(ProfilePreferences::personal).forEach { personal.put(it,values.remove(it)) }
                val legacyHints=values.remove("personal_hints"); val targetCadence=values.remove("target_cadence")
                if(personal.length()>0 || legacyHints!=null || targetCadence!=null) {
                    val legacy=dao.user(LEGACY_USER_ID) ?: UserProfile(LEGACY_USER_ID,"Legacy user",legacyHints="{\"version\":1}").row()
                    legacy.pendingPreferences=JSONObject(settings.toString()).put("values",personal).toString()
                    legacy.legacyHints=JSONObject().put("version",1).put("personal_hints",legacyHints).put("target_cadence",targetCadence).toString()
                    if(personal.has("weight_kg")) legacy.weightKg=personal.getDouble("weight_kg")
                    if(personal.has("estimate_met")) { legacy.met=personal.getDouble("estimate_met"); legacy.metSource="legacy" }
                    dao.user(legacy)
                }
                // Global restore is limited to the shared group; never clear another person's file.
                settings.put("groups",JSONArray(listOf(PreferenceGroup.DEVICES.name)))
                if(PreferenceGroup.DEVICES in groups) dao.pendingPreferences(PreferenceRestoreRow().apply { json=settings.toString() })
            }
        }
        committed=true
        // Past the durable DB commit cancellation must finish/recover the preference journal.
        withContext(NonCancellable) { repo.recoverPreferences(); repo.recoverUserPreferences(); repo.recoverAvatarCleanup(); repo.refresh() }
        added
        } } catch(e: CancellationException) {
            if(!committed) throw e
            // Cancellation can race the dispatcher hand-off after a successful commit.
            // Report the durable result, rather than claiming it was rolled back.
            withContext(NonCancellable+Dispatchers.IO) { repo.recoverPreferences(); repo.recoverUserPreferences(); repo.recoverAvatarCleanup(); repo.refresh() }
            return added
        }
    }
}
