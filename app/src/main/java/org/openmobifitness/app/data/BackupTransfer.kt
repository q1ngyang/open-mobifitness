package org.openmobifitness.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.*
import org.openmobifitness.core.*
import java.io.*
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.*

data class RestorePreview(val sessions: Long,val samples: Long,val workouts: Long,val added: Long,val duplicates: Long,val conflicts: Long)
class StagedBackup internal constructor(val directory: File,val db: MobiDatabase,val preferences: String?,val preview: RestorePreview): Closeable {
    override fun close() { db.close(); directory.deleteRecursively() }
}
/** ZIP v7: independently parseable CSV chunks; CSV schemas remain session v5/sample v6.
 * Memory is bounded by a chunk, SQLite cursors and a single CSV row, not archive size. */
object BackupTransfer {
    const val MAX_BYTES=2L*1024*1024*1024
    const val MAX_ENTRIES=20000
    private const val PAGE=2000
    suspend fun export(repo: Repository,output: OutputStream,preferences: String?,progress: (Long)->Unit={})=withContext(Dispatchers.IO) {
        val job=currentCoroutineContext()[Job]!!; var bytes=0L; var entries=0
        ZipOutputStream(output).use { zip ->
            fun entry(name: String,value: String) {
                if(name.endsWith(".csv")) Exchange.parse(value)
                job.ensureActive(); val data=value.toByteArray(Charsets.UTF_8); bytes+=data.size
                require(bytes<=MAX_BYTES && ++entries<=MAX_ENTRIES) { "backup_capacity" }
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry(); progress(bytes)
            }
            repo.db.runInTransaction {
                entry("manifest.txt","OpenMOBI backup 7\n")
                preferences?.let { PortablePreferences.validate(it); entry("preferences.json",it) }
                var after=""; var part=0
                do { val page=repo.db.records().sessionPage(after,PAGE); entry("sessions/${part++}.csv",Exchange.sessions(page.map { it.model() })); if(page.isEmpty()) break; after=page.last().id } while(true)
                after=""; var time=-1L; part=0
                do { val page=repo.db.records().samplePage(after,time,PAGE); entry("samples/${part++}.csv",Exchange.samples(page.map { it.model() })); if(page.isEmpty()) break; after=page.last().sessionId; time=page.last().elapsedMs } while(true)
                after=""; part=0
                do {
                    val page=repo.db.records().workoutPage(after,100)
                    if(page.isEmpty()) { if(part==0) entry("workouts/0.csv",Exchange.workouts(emptyList())); break }
                    entry("workouts/${part++}.csv",Exchange.workouts(page.flatMap { Exchange.parse(it.csv).workouts })); after=page.last().id
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
                    require(!entry.isDirectory && (entry.name in setOf("manifest.txt","sessions.csv","samples.csv","workouts.csv","preferences.json") || entry.name.matches(Regex("(sessions|samples|workouts)/[0-9]{1,6}\\.csv")))) { "backup_entry" }
                    copy(entry.name,stream); stream.closeEntry()
                }
            } else copy("input.csv",buffered)
            if(zip) {
                val manifest=parts["manifest.txt"] ?: error("backup_manifest")
                require(manifest.length()<128)
                val text=manifest.readText()
                if(text=="OpenMOBI backup 7\n") {
                    require(parts.keys.none { it in setOf("sessions.csv","samples.csv","workouts.csv") })
                    listOf("sessions","samples","workouts").forEach { type ->
                        val numbers=parts.keys.filter { it.startsWith("$type/") }.map { it.substringAfter('/').substringBefore('.').toInt() }.sorted()
                        require(numbers.isNotEmpty() && numbers==numbers.indices.toList()) { "backup_chunks" }
                    }
                } else { require(text in setOf("OpenMobi backup 1\n","OpenMobi backup 2\n","OpenMOBI backup 3\n","OpenMOBI backup 4\n","OpenMOBI backup 5\n","OpenMOBI backup 6\n")); require(parts.keys==setOf("manifest.txt","sessions.csv","samples.csv","workouts.csv")) }
            }
            val prefs=parts["preferences.json"]?.let { require(it.length()<=1024*1024); it.readText().also(PortablePreferences::validate) }
            staged=Room.databaseBuilder(context,MobiDatabase::class.java,File(directory,"staged.db").absolutePath).build()
            val dao=staged.records()
            var parentCache: SessionRow?=null
            val ordered=parts.filterKeys { it.endsWith(".csv") }.entries.sortedWith(compareBy({ when { it.key.startsWith("sessions") -> 0; it.key.startsWith("samples") -> 2; else -> 1 } },{ it.key }))
            staged.runInTransaction {
                ordered.forEach { (name,file) ->
                    fun insert(archive: Archive) {
                        job.ensureActive()
                        archive.sessions.forEach { s -> require(dao.session(s.id)==null) { "duplicate_session" }; dao.session(s.row()) }
                        archive.workouts.forEach { w -> require(dao.workoutAt(w.id)==null) { "duplicate_workout" }; dao.workout(WorkoutRow().apply { id=w.id; csv=Exchange.workouts(listOf(w)) }) }
                        val samples=archive.samples.map { s ->
                            job.ensureActive()
                            val parent=parentCache?.takeIf { it.id==s.sessionId } ?: (dao.session(s.sessionId) ?: repo.db.records().session(s.sessionId)?.also { dao.session(it) } ?: error("sample_reference")).also { parentCache=it }
                            require(s.elapsedMs<=parent.elapsedMs) { "sample_reference" }; s.row()
                        }
                        if(samples.isNotEmpty()) dao.samples(samples)
                    }
                    parseCsv(file,name,job,::insert)
                }
            }
            val preview=inspect(repo.db,staged,job)
            StagedBackup(directory,staged,prefs,preview).also { completed=it }
        } catch(e: Throwable) { staged?.close(); directory.deleteRecursively(); throw e }
        } } catch(e: Throwable) { withContext(NonCancellable+Dispatchers.IO) { completed?.close() }; throw e }
    }
    private fun parseCsv(file: File,name: String,job: Job,consume: (Archive)->Unit) {
        val reader=InputStreamReader(file.inputStream(),Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        reader.use {
            var header: List<String>?=null; val batch=mutableListOf<List<String>>(); var workout=false; var rows=0
            fun flush() { if(batch.isNotEmpty()) { consume(Exchange.parse(Csv.write(listOf(header!!)+batch))); batch.clear() } }
            Csv.stream(reader) { row ->
                job.ensureActive()
                if(header==null) {
                    Exchange.parse(Csv.write(listOf(row))); header=row; workout=row.getOrNull(1)=="workout_id"
                    if(name!="input.csv") require(when { name.startsWith("sessions") -> row.getOrNull(2)=="start_utc"; name.startsWith("samples") -> row.getOrNull(2)=="elapsed_ms"; else -> workout })
                } else { batch.add(row); rows++; if(workout) require(rows<=200000 && file.length()<=32*1024*1024) { "workout_capacity" }; if(!workout && batch.size>=PAGE) flush() }
            }
            require(header!=null); flush()
        }
    }
    private fun compatible(old: Session,row: Session)=old.status!="active" && (old.copy(archived=false)==row.copy(archived=false) || old.copy(archived=false)==row.copy(status="interrupted",archived=false))
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
        while(true) { job.ensureActive(); val page=dao.sessionPage(after,PAGE); if(page.isEmpty()) break; page.forEach(session); after=page.last().id }
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
            when { old==null -> added++; Exchange.parse(old.csv)==Exchange.parse(row.csv) -> duplicates++; else -> conflicts++ }
        })
        return RestorePreview(source.records().sessionCount(),source.records().sampleCount(),source.records().workoutCount(),added,duplicates,conflicts)
    }
    suspend fun restore(repo: Repository,staged: StagedBackup,groups: Set<PreferenceGroup>,progress: (Long)->Unit={},finalizing: ()->Unit={}): Long {
        var committed=false
        var added=0L
        try { return withContext(Dispatchers.IO) {
        val job=currentCoroutineContext()[Job]!!; val dao=repo.db.records(); var processed=0L
        require(staged.preview.conflicts==0L) { "restore_conflict" }
        repo.db.runInTransaction {
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
                val old=dao.workoutAt(row.id); require(old==null || Exchange.parse(old.csv)==Exchange.parse(row.csv)) { "workout_conflict" }
                if(old==null) { dao.workout(row); added++ }; progress(++processed)
            })
            flush(); finalizing(); job.ensureActive()
            if(groups.isNotEmpty() && staged.preferences!=null) dao.pendingPreferences(PreferenceRestoreRow().apply { json=PortablePreferences.select(staged.preferences,groups) })
        }
        committed=true
        // Past the durable DB commit cancellation must finish/recover the preference journal.
        withContext(NonCancellable) { repo.recoverPreferences(); repo.refresh() }
        added
        } } catch(e: CancellationException) {
            if(!committed) throw e
            // Cancellation can race the dispatcher hand-off after a successful commit.
            // Report the durable result, rather than claiming it was rolled back.
            withContext(NonCancellable+Dispatchers.IO) { repo.recoverPreferences(); repo.refresh() }
            return added
        }
    }
}
