package org.openmobifitness.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.openmobifitness.core.Version
import java.net.HttpURLConnection
import java.net.URL

data class Release(val name: String,val url: String)
object Updates {
    const val REPOSITORY="https://github.com/q1ngyang/open-mobifitness"
    suspend fun check(current: String): Release? = withContext(Dispatchers.IO) {
        val connection=URL("https://api.github.com/repos/q1ngyang/open-mobifitness/releases?per_page=30").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=8000; connection.readTimeout=8000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("User-Agent","OpenMOBI/$current")
            if(connection.responseCode==404) return@withContext null
            check(connection.responseCode==200)
            val bytes=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer); if(n<0) break; require(output.size()+n<=1_048_576); output.write(buffer,0,n) }
                output.toByteArray()
            }
            val installed=Version.parse(current) ?: return@withContext null
            val releases=JSONArray(bytes.toString(Charsets.UTF_8))
            (0 until releases.length()).mapNotNull { index ->
                val json=releases.getJSONObject(index)
                if(json.optBoolean("draft") || (installed.pre.isEmpty() && json.optBoolean("prerelease"))) return@mapNotNull null
                val tag=json.getString("tag_name").removePrefix("v")
                val version=Version.parse(tag) ?: return@mapNotNull null
                if(version<=installed || (installed.pre.isEmpty() && version.pre.isNotEmpty())) return@mapNotNull null
                val url=json.getString("html_url"); if(!url.startsWith("$REPOSITORY/releases/tag/")) return@mapNotNull null
                version to Release(tag,url)
            }.maxByOrNull { it.first }?.second
        } finally { connection.disconnect() }
    }
}
