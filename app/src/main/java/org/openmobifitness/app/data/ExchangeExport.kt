package org.openmobifitness.app.data

import kotlinx.coroutines.*
import org.openmobifitness.core.*
import java.io.OutputStream

/** Frozen selection, one CSV header, bounded pages. No full sample archive in memory. */
object ExchangeExport {
    suspend fun write(repo: Repository,kind: String,query: HistoryQuery,sessionId: String?,ownerId: String?,output: OutputStream)=withContext(Dispatchers.IO) {
        var first=true
        fun chunk(csv: String) {
            val text=if(first) csv else csv.substringAfter("\r\n","").removePrefix("\uFEFF")
            output.write(text.toByteArray(Charsets.UTF_8)); first=false
        }
        if(kind=="workouts") {
            chunk(Exchange.workouts(if(ownerId==null) emptyList() else repo.db.records().userWorkouts(ownerId).map { it.model() }))
        } else {
            val ids=if(sessionId!=null) listOf(sessionId) else repo.history.reportIds(query)
            if(kind=="samples") {
                chunk(Exchange.samples(emptyList()))
                ids.forEach { id ->
                    var after=-1L
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val page=repo.db.records().samplesForPage(id,after,1000); if(page.isEmpty()) break
                        chunk(Exchange.samples(page.map { it.model() })); after=page.last().elapsedMs
                    }
                }
            } else {
                chunk(Exchange.sessions(emptyList()))
                ids.chunked(32).forEach { page -> currentCoroutineContext().ensureActive(); chunk(Exchange.sessions(page.mapNotNull { repo.db.records().session(it)?.model() })) }
            }
        }
    }
}
