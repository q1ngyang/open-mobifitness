package org.openmobifitness.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.openmobifitness.core.*
import java.time.Instant
import java.util.UUID

internal fun UserRow.model()=UserProfile(id,name,weightKg,met,metSource,avatar,createdAt,removedAt,deleted,pendingPreferences,legacyHints)
internal fun UserProfile.row()=UserRow().also {
    it.id=id; it.name=name; it.weightKg=weightKg; it.met=met; it.metSource=metSource; it.avatar=avatar; it.createdAt=createdAt
    it.removedAt=removedAt; it.deleted=deleted; it.pendingPreferences=preferences; it.legacyHints=legacyHints
}
internal fun WorkoutRow.model(): Workout {
    val parsed=Exchange.parse(csv).workouts.single()
    return if(parsed.machine==null) parsed.copy(ownerUserId=ownerUserId ?: parsed.ownerUserId,machine=if(parsed.steps.any { it.condition==Condition.STROKES }) Machine.ROWER else null,legacyUnclassified=parsed.steps.none { it.condition==Condition.STROKES })
    else parsed.copy(ownerUserId=ownerUserId ?: parsed.ownerUserId)
}
internal fun Workout.row()=WorkoutRow().also { it.id=id; it.ownerUserId=ownerUserId; it.csv=Exchange.workouts(listOf(this)) }

internal fun Repository.migrateLegacyProfile() {
    val dao=db.records(); val original=dao.user(LEGACY_USER_ID) ?: return
    if(original.legacyHints.isNotEmpty()) return
    val portable=PortablePreferences.validate(PortablePreferences.export(preferences))
    val values=portable.getJSONObject("values")
    values.keys().asSequence().toList().filter { !ProfilePreferences.personal(it) }.forEach(values::remove)
    val hints=JSONObject().put("version",1).put("personal_hints",preferences.getString("personal_hints",null)).put("target_cadence",if(preferences.contains("target_cadence")) preferences.getInt("target_cadence",24) else JSONObject.NULL)
    if(original.name=="Legacy user") original.name=context.getString(org.openmobifitness.app.R.string.user_legacy)
    original.pendingPreferences=portable.toString()
    original.weightKg=if(preferences.contains("weight_kg")) preferences.getFloat("weight_kg",70f).toDouble() else null
    original.met=preferences.getFloat("estimate_met",5f).toDouble()
    original.metSource=if(preferences.contains("estimate_met")) "legacy" else "default"
    original.legacyHints=hints.toString()
    dao.user(original)
    if(!preferences.contains("active_user_id")) profilePreferences.select(original.id)
}
internal fun Repository.recoverUserPreferences() {
    val dao=db.records()
    dao.users().forEach { user ->
        if(user.deleted) {
            check(profilePreferences.forUser(user.id).edit().clear().commit())
        } else if(user.pendingPreferences.isNotEmpty()) {
            PortablePreferences.validate(user.pendingPreferences).getJSONObject("values").keys().forEach { require(ProfilePreferences.personal(it)) { "personal_preference_scope" } }
            check(PortablePreferences.apply(profilePreferences.forUser(user.id),user.pendingPreferences)) { "user_preference_restore_pending" }
            dao.clearUserPreferences(user.id)
        }
    }
}
internal fun Repository.exportUser(row: UserRow)=row.model().copy(preferences=if(row.deleted) "" else PortablePreferences.export(profilePreferences.forUser(row.id)))

suspend fun Repository.saveUser(user: UserProfile)=withContext(Dispatchers.IO) {
    val dao=db.records()
    db.runInTransaction {
        require(user.available) { "user_removed" }
        require(dao.activeSessions().none { it.startedUserId==user.id || it.ownerUserId==user.id }) { "identity_locked" }
        require(dao.user(user.id)?.removedAt==null) { "user_removed" }
        require(dao.users().none { it.id!=user.id && it.removedAt==null && it.model().normalizedName==user.normalizedName }) { "duplicate_user_name" }
        require(user.avatar.isEmpty() || avatars.file(user.avatar).isFile) { "avatar_missing" }
        dao.user(user.id)?.avatar?.takeIf { it!=user.avatar }?.let { avatars.queueCleanup(setOf(it)) }
        dao.user(user.copy(preferences="").row())
    }
    recoverAvatarCleanup()
    refresh()
}

/** Release only this editor's candidates; another profile can legitimately share a photo hash. */
suspend fun Repository.discardAvatars(candidates: Set<String>)=withContext(Dispatchers.IO) {
    avatars.queueCleanup(candidates)
    recoverAvatarCleanup()
}
internal fun Repository.recoverAvatarCleanup() {
    db.runInTransaction { avatars.finishCleanup(db.records().users().map { it.avatar }.toSet()) }
}

data class UserRemovalPreview(val userId: String,val sessions: Int,val workouts: Int,val fingerprint: String)
suspend fun Repository.removalPreview(id: String)=withContext(Dispatchers.IO) { removalSnapshot(id) }
private fun Repository.removalSnapshot(id: String): UserRemovalPreview {
    val dao=db.records(); val user=dao.user(id) ?: error("user_missing")
    val sessions=dao.userSessionIds(id); val workouts=dao.userWorkouts(id)
    val state=listOf(user.id,user.name,user.removedAt,user.deleted,sessions,workouts.map { it.id to it.csv }).joinToString("|")
    return UserRemovalPreview(id,sessions.size,workouts.size,AvatarStore.hash(state.toByteArray()))
}
suspend fun Repository.removeUser(preview: UserRemovalPreview,deleteData: Boolean,confirmed: Boolean)=withContext(Dispatchers.IO) {
    val dao=db.records()
    db.runInTransaction {
        require(removalSnapshot(preview.userId)==preview) { "removal_preview_changed" }
        require(!deleteData || confirmed) { "delete_confirmation_required" }
        require(dao.activeSessions().none { it.ownerUserId==preview.userId || it.startedUserId==preview.userId }) { "identity_locked" }
        val row=dao.user(preview.userId) ?: error("user_missing")
        if(deleteData) {
            avatars.queueCleanup(setOf(row.avatar))
            dao.userSessionIds(row.id).forEach(dao::deleteSession)
            dao.userWorkouts(row.id).forEach { dao.deleteWorkout(it.id) }
            row.name="Removed user"; row.weightKg=null; row.met=5.0; row.metSource="default"; row.avatar=""; row.pendingPreferences=""; row.legacyHints=""; row.deleted=true
        }
        dao.user(row); softRemoveUserRow(row.id,System.currentTimeMillis())
    }
    if(deleteData) recoverUserPreferences()
    if(profilePreferences.userId==preview.userId) profilePreferences.select(null)
    refresh()
    recoverAvatarCleanup()
}
/** Caller owns a Room transaction and the controller identity lock. */
internal fun Repository.softRemoveUserRow(id: String,at: Long) {
    val dao=db.records()
    require(dao.activeSessions().none { it.ownerUserId==id || it.startedUserId==id }) { "identity_locked" }
    val row=dao.user(id) ?: error("user_missing")
    row.removedAt=row.removedAt ?: at; dao.user(row)
}
suspend fun Repository.restoreUser(id: String)=withContext(Dispatchers.IO) {
    val dao=db.records()
    db.runInTransaction {
        val row=dao.user(id) ?: error("user_missing"); require(!row.deleted) { "user_deleted" }
        require(dao.users().none { it.id!=id && it.removedAt==null && it.model().normalizedName==row.model().normalizedName }) { "duplicate_user_name" }
        row.removedAt=null; dao.user(row)
    }; refresh()
}

/** Owner changes never rewrite the start identity, body estimates, measurements or plan snapshot. */
suspend fun Repository.changeOwners(ids: List<String>,ownerId: String,expectedOwners: Map<String,String?>)=withContext(Dispatchers.IO) {
    require(ids.isNotEmpty() && ids.distinct().size==ids.size)
    val dao=db.records()
    db.runInTransaction {
        require(dao.user(ownerId)?.model()?.available==true) { "user_required" }
        ids.forEach { id ->
            val session=dao.session(id) ?: error("session_missing")
            require(session.status!="active" && expectedOwners.containsKey(id) && session.ownerUserId==expectedOwners[id]) { "owner_preview_changed" }
            if(session.ownerUserId!=ownerId) {
                val history=JSONArray(session.ownerHistory.ifEmpty { "[]" })
                require(history.length()<1000) { "owner_history_limit" }
                history.put(JSONObject().put("id",UUID.randomUUID().toString()).put("at",Instant.now().toString()).put("from",session.ownerUserId ?: JSONObject.NULL).put("to",ownerId))
                session.ownerHistory=history.toString(); session.ownerUserId=ownerId; dao.session(session)
            }
        }
    }; refresh()
}
