package org.openmobifitness.app.data

import org.openmobifitness.core.*
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

object Files {
    private const val LIMIT=32*1024*1024
    private fun InputStream.bounded(limit: Int=LIMIT): ByteArray {
        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { val n=read(buffer); if(n<0) break; require(out.size()+n<=limit); out.write(buffer,0,n) }
        return out.toByteArray()
    }
    fun read(input: InputStream): Archive {
        val bytes=input.bounded()
        fun decode(bytes: ByteArray)=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        if(bytes.size<4 || bytes[0]!=0x50.toByte() || bytes[1]!=0x4b.toByte()) return Exchange.parse(decode(bytes))
        val pieces=mutableMapOf<String,Archive>(); var size=0
        val seen=HashSet<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("sessions.csv","samples.csv","workouts.csv","manifest.txt") && seen.add(entry.name))
                val part=zip.bounded(LIMIT-size); size+=part.size
                if(entry.name=="manifest.txt") require(decode(part)=="OpenMobi backup 1\n")
                else {
                    val text=decode(part)
                    val prefix=when(entry.name) { "sessions.csv" -> "schema,session_id,start_utc,"; "samples.csv" -> "schema,session_id,elapsed_ms,"; else -> "schema,workout_id,title," }
                    require(text.removePrefix("\uFEFF").startsWith(prefix))
                    pieces[entry.name]=Exchange.parse(text)
                }
            }
        }
        require(seen==setOf("sessions.csv","samples.csv","workouts.csv","manifest.txt"))
        return Archive(pieces.values.flatMap { it.sessions },pieces.values.flatMap { it.samples },pieces.values.flatMap { it.workouts })
    }
    fun backup(output: OutputStream,archive: Archive) {
        ZipOutputStream(output).use { zip ->
            val entries=linkedMapOf("manifest.txt" to "OpenMobi backup 1\n","sessions.csv" to Exchange.sessions(archive.sessions),"samples.csv" to Exchange.samples(archive.samples),"workouts.csv" to Exchange.workouts(archive.workouts))
            entries.forEach { (name,text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
        }
    }
}
