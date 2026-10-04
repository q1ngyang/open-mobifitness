package org.openmobifitness.app.data

import android.content.Context
import android.content.SharedPreferences

/** One shared BLE/application domain, with a separate durable file for each stable person ID. */
class ProfilePreferences(private val context: Context,private val shared: SharedPreferences): SharedPreferences {
    @Volatile var userId: String?=shared.getString("active_user_id",null)
        private set
    companion object {
        fun personal(key: String)=PortablePreferences.group(key)!=null && key !in setOf("saved_devices","language","packet_logs","identity_policy","personal_hints","target_cadence")
    }
    fun forUser(id: String)=context.getSharedPreferences("profile_$id",Context.MODE_PRIVATE)
    fun select(id: String?) {
        check(shared.edit().putString("active_user_id",id).commit()) { "preference_write_failed" }
        userId=id
    }
    private fun target(key: String)=if(personal(key)) forUser(userId ?: "unselected") else shared
    override fun getAll(): MutableMap<String,*> = (shared.all.filterKeys { !personal(it) } + forUser(userId ?: "unselected").all.filterKeys(::personal)).toMutableMap()
    override fun contains(key: String)=target(key).contains(key)
    override fun getString(key: String,defValue: String?)=target(key).getString(key,defValue)
    override fun getStringSet(key: String,defValues: Set<String>?)=target(key).getStringSet(key,defValues)
    override fun getInt(key: String,defValue: Int)=target(key).getInt(key,defValue)
    override fun getLong(key: String,defValue: Long)=target(key).getLong(key,defValue)
    override fun getFloat(key: String,defValue: Float)=target(key).getFloat(key,defValue)
    override fun getBoolean(key: String,defValue: Boolean)=target(key).getBoolean(key,defValue)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { shared.registerOnSharedPreferenceChangeListener(listener); forUser(userId ?: "unselected").registerOnSharedPreferenceChangeListener(listener) }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { shared.unregisterOnSharedPreferenceChangeListener(listener); forUser(userId ?: "unselected").unregisterOnSharedPreferenceChangeListener(listener) }
    override fun edit(): SharedPreferences.Editor {
        // Capture the identity here: a delayed apply cannot write into a subsequently selected user.
        val personalEditor=forUser(userId ?: "unselected").edit(); val common=shared.edit()
        return object: SharedPreferences.Editor {
            fun editor(key: String)=if(personal(key)) personalEditor else common
            override fun putString(key: String,value: String?)=apply { editor(key).putString(key,value) }
            override fun putStringSet(key: String,values: Set<String>?)=apply { editor(key).putStringSet(key,values) }
            override fun putInt(key: String,value: Int)=apply { editor(key).putInt(key,value) }
            override fun putLong(key: String,value: Long)=apply { editor(key).putLong(key,value) }
            override fun putFloat(key: String,value: Float)=apply { editor(key).putFloat(key,value) }
            override fun putBoolean(key: String,value: Boolean)=apply { editor(key).putBoolean(key,value) }
            override fun remove(key: String)=apply { editor(key).remove(key) }
            override fun clear()=apply { all.keys.forEach { editor(it).remove(it) } }
            override fun commit(): Boolean { val a=personalEditor.commit(); val b=common.commit(); return a && b }
            override fun apply() { personalEditor.apply(); common.apply() }
        }
    }
}
