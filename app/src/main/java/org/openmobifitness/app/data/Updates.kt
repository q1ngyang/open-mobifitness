package org.openmobifitness.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Release(val name: String,val url: String)
object Updates {
    const val REPOSITORY="https://github.com/q1ngyang/open-mobifitness"
    suspend fun check(current: String): Release? = withContext(Dispatchers.IO) {
        val connection=URL("https://api.github.com/repos/q1ngyang/open-mobifitness/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=8000; connection.readTimeout=8000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("User-Agent","OpenMobi/$current")
            if(connection.responseCode==404) return@withContext null
            check(connection.responseCode==200)
            val bytes=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer); if(n<0) break; require(output.size()+n<=1_048_576); output.write(buffer,0,n) }
                output.toByteArray()
            }
            val json=JSONObject(bytes.toString(Charsets.UTF_8)); if(json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
            val tag=json.getString("tag_name").removePrefix("v")
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toInt() }.also { require(it.size==3) }
            val a=parts(tag); val b=parts(current); val cmp=a.zip(b).firstOrNull { it.first!=it.second }?.let { it.first.compareTo(it.second) } ?: 0
            if(cmp<0 || (cmp==0 && '-' !in current)) return@withContext null
            val url=json.getString("html_url"); require(url.startsWith("$REPOSITORY/releases/tag/"))
            Release(tag,url)
        } finally { connection.disconnect() }
    }
}
