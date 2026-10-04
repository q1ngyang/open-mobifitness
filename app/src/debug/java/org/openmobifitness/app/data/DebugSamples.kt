package org.openmobifitness.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openmobifitness.app.Controller
import org.openmobifitness.core.*
import java.time.Instant
import java.time.ZoneId

internal data class DebugSampleInfo(val profiles: Int=0,val sessions: Int=0,val from: Long=0,val until: Long=0)
internal data class DebugClearPreview(val ids: Set<String>,val removeUsers: Set<String>,val keptUsers: Set<String>,val fingerprint: String)

/** The controller lock excludes starts, identity changes and backup commits at every entry. */
internal class DebugSamples(private val c: Controller) {
    private val store=DebugSampleStore(c.repo)
    val progress=MutableStateFlow<Int?>(null)
    suspend fun info()=withContext(Dispatchers.IO) { store.info() }
    suspend fun preview()=withContext(Dispatchers.IO) { store.preview() }
    suspend fun load(names: List<String>): DebugSampleInfo {
        var result=DebugSampleInfo()
        c.idleOperation { try { progress.value=0; result=withContext(Dispatchers.IO) { store.load(names,progress={ progress.value=it }) } } finally { progress.value=null } }
        return result
    }
    suspend fun clear(preview: DebugClearPreview): DebugSampleInfo {
        var result=DebugSampleInfo(); val previous=c.currentUser.value?.id
        c.idleOperation { try { progress.value=0; result=withContext(Dispatchers.IO) { store.clear(preview) }; progress.value=100 } finally { progress.value=null } }
        if(previous!=null && c.currentUser.value==null) c.userPicker.value=true
        return result
    }
}

/** Small private journal. Persist intent before the DB transaction; reconcile after interruption. */
internal class DebugSampleStore(private val repo: Repository,private val now: ()->Instant={ Instant.now() },private val zone: ()->ZoneId={ ZoneId.systemDefault() }) {
    private val prefs=repo.context.getSharedPreferences("debug_sample_dataset",Context.MODE_PRIVATE)
    private val dao get()=repo.db.records()
    private fun read(): JSONObject {
        val state=JSONObject(prefs.getString("state","{}")!!)
        require(state.optInt("version",DebugDataset.VERSION)==DebugDataset.VERSION) { "debug_dataset_version" }
        if(state.optBoolean("clearing")) {
            if(DebugDataset.sessionIds.none { dao.session(it)!=null }) { state.remove("anchor"); state.remove("zone") }
            state.remove("clearing"); write(state)
        }
        return state
    }
    private fun write(state: JSONObject) { state.put("version",DebugDataset.VERSION); check(prefs.edit().putString("state",state.toString()).commit()) { "debug_state_write" } }
    fun info(): DebugSampleInfo {
        read()
        val rows=DebugDataset.sessionIds.mapNotNull(dao::session).filter { it.demo }
        return DebugSampleInfo(DebugDataset.userIds.count { dao.user(it)!=null },rows.size,rows.minOfOrNull { it.startEpoch } ?: 0,rows.maxOfOrNull { Instant.parse(it.end).toEpochMilli()+1 } ?: 0)
    }
    fun load(names: List<String>,progress: (Int)->Unit={},beforeInsert: (Int)->Unit={}): DebugSampleInfo {
        require(names.size==4)
        val state=read()
        if(!state.has("anchor")) { state.put("anchor",now().toString()).put("zone",zone().id); write(state) }
        val anchor=Instant.parse(state.getString("anchor")); val zone=ZoneId.of(state.getString("zone"))
        val toolRemoved=state.optJSONObject("toolRemoved") ?: JSONObject()
        repo.db.runInTransaction {
            check(dao.activeSessions().isEmpty()) { "identity_locked" }
            DebugDataset.users(anchor,zone,names).forEachIndexed { index,user ->
                val existing=dao.user(user.id)
                if(existing==null) {
                    require(dao.users().none { it.removedAt==null && it.model().normalizedName==user.normalizedName }) { "duplicate_user_name" }
                    dao.user(user.row())
                } else {
                    require(!existing.deleted) { "debug_user_deleted" }
                    // Only a matching tool-removal journal may reactivate a profile.
                    if(index<3 && existing.removedAt!=null && toolRemoved.optLong(user.id,-1)==existing.removedAt) {
                        require(dao.users().none { it.id!=user.id && it.removedAt==null && it.model().normalizedName==existing.model().normalizedName }) { "duplicate_user_name" }
                        existing.removedAt=null; dao.user(existing)
                    }
                }
            }
            DebugDataset.entries.forEachIndexed { index,entry ->
                val fixture=DebugDataset.fixture(entry,anchor,zone,names); val s=fixture.session
                beforeInsert(index)
                val existing=dao.session(s.id)
                if(existing==null) dao.session(s.row()) else {
                    // Owner corrections, profile renames, archive flags and old localized names survive.
                    require(existing.demo && existing.protocol==Protocol.DEMO.name && existing.device==s.device && existing.start==s.start && existing.end==s.end && existing.zone==s.zone && existing.machine==s.machine.name && existing.elapsedMs==s.elapsedMs && existing.distanceM==s.distanceM && existing.caloriesKcal==s.caloriesKcal && existing.status==s.status) { "debug_sample_conflict" }
                }
                val oldSamples=dao.samplesFor(s.id).associateBy { it.elapsedMs }
                val expectedTimes=fixture.samples.map { it.elapsedMs }.toHashSet()
                require(oldSamples.keys.all { it in expectedTimes }) { "debug_sample_conflict" }
                val missing=fixture.samples.filter { sample -> oldSamples[sample.elapsedMs]?.let { require(it.model()==sample) { "debug_sample_conflict" }; false } ?: true }
                missing.chunked(500).forEach { dao.samples(it.map(Sample::row)) }
                if(index%10==0 || index==DebugDataset.entries.lastIndex) progress((index+1)*100/DebugDataset.entries.size)
            }
        }
        // Rows no longer match old removal timestamps after successful reactivation.
        repo.refresh(); return info()
    }
    private fun externalReferences(id: String,deleting: Set<String>): Boolean {
        if(dao.userWorkoutCount(id)>0) return true
        var after=""
        while(true) {
            val rows=dao.sessionPage(after,100)
            if(rows.isEmpty()) return false
            if(rows.any { it.id !in deleting && (it.ownerUserId==id || it.startedUserId==id || it.ownerHistory.contains(id)) }) return true
            after=rows.last().id
        }
    }
    fun preview(): DebugClearPreview {
        read()
        val rows=DebugDataset.sessionIds.mapNotNull(dao::session)
        val ids=rows.filter { it.demo }.map { it.id }.toSet()
        val profiles=DebugDataset.userIds.mapNotNull(dao::user)
        val keep=profiles.filter { externalReferences(it.id,ids) }.map { it.id }.toSet()
        val remove=profiles.filter { it.id !in keep && it.removedAt==null }.map { it.id }.toSet()
        val fingerprint=AvatarStore.hash((rows.sortedBy { it.id }.joinToString { "${it.id}/${it.demo}/${it.status}/${it.ownerUserId}/${it.startedUserId}/${it.ownerHistory}" }+profiles.joinToString { "${it.id}/${it.name}/${it.removedAt}/${it.deleted}" }+keep.sorted().joinToString()).toByteArray())
        return DebugClearPreview(ids,remove,keep,fingerprint)
    }
    fun clear(expected: DebugClearPreview): DebugSampleInfo {
        val state=read(); val removed=state.optJSONObject("toolRemoved") ?: JSONObject()
        // Intent can survive a process stop. A timestamp is honored only if the DB row matches it.
        val removedAt=now().toEpochMilli()
        expected.removeUsers.forEach { removed.put(it,removedAt) }
        state.put("toolRemoved",removed).put("clearing",true); write(state)
        try {
            repo.db.runInTransaction {
                // Avoid read() here: it would prematurely reconcile this transaction's intent.
                state.remove("clearing"); write(state)
                require(preview()==expected) { "removal_preview_changed" }
                state.put("clearing",true); write(state)
                check(dao.activeSessions().isEmpty()) { "identity_locked" }
                expected.ids.forEach { id -> val row=dao.session(id); require(row!=null && row.demo); dao.deleteSession(id) }
                expected.removeUsers.forEach { id -> repo.softRemoveUserRow(id,removedAt) }
            }
        } catch(e: Exception) { state.remove("clearing"); write(state); throw e }
        if(repo.profilePreferences.userId in expected.removeUsers) repo.profilePreferences.select(null)
        if(DebugDataset.sessionIds.none { dao.session(it)!=null }) { state.remove("anchor"); state.remove("zone") }
        state.remove("clearing"); write(state)
        repo.refresh(); return info()
    }
}
