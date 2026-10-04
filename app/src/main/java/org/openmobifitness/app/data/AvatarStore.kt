package org.openmobifitness.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Selected content URIs are decoded once; only an app-owned, canonical JPEG is retained. */
class AvatarStore(private val context: Context) {
    private val cleanup=context.getSharedPreferences("avatar_cleanup",Context.MODE_PRIVATE)
    val directory: File get()=File(context.filesDir,"avatars").apply { mkdirs() }
    fun file(reference: String): File {
        require(reference.matches(Regex("[a-f0-9]{64}\\.jpg")))
        return File(directory,reference)
    }
    fun select(uri: Uri): String {
        val encoded=context.contentResolver.openInputStream(uri)?.use { input -> ByteArrayOutputStream().use { out -> val buffer=ByteArray(8192); while(true) { val n=input.read(buffer); if(n<0) break; require(out.size()+n<=20*1024*1024) { "avatar_too_large" }; out.write(buffer,0,n) }; out.toByteArray() } } ?: error("avatar_unreadable")
        require(encoded.size<=20*1024*1024) { "avatar_too_large" }
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(encoded))) { decoder,info,_ ->
            require(info.size.width.toLong()*info.size.height<=120_000_000L) { "avatar_too_large" }
            val factor=minOf(512.0/minOf(info.size.width,info.size.height),4096.0/maxOf(info.size.width,info.size.height))
            decoder.setTargetSize((info.size.width*factor).toInt().coerceAtLeast(1).coerceAtMost(4096),(info.size.height*factor).toInt().coerceAtLeast(1).coerceAtMost(4096))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val square=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val side=minOf(bitmap.width,bitmap.height); val x=(bitmap.width-side)/2; val y=(bitmap.height-side)/2
        Canvas(square).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap,Rect(x,y,x+side,y+side),Rect(0,0,512,512),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
        val bytes=ByteArrayOutputStream().use { square.compress(Bitmap.CompressFormat.JPEG,90,it); it.toByteArray() }
        bitmap.recycle(); square.recycle()
        val reference=hash(bytes)+".jpg"; val destination=file(reference)
        if(!destination.exists()) { val pending=File(directory,"$reference.pending"); pending.writeBytes(bytes); check(pending.renameTo(destination)) }
        return reference
    }
    fun verify(reference: String,bytes: ByteArray) {
        require(bytes.size<=2*1024*1024 && reference==hash(bytes)+".jpg") { "avatar_hash_mismatch" }
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outMimeType=="image/jpeg" && bounds.outWidth==512 && bounds.outHeight==512) { "avatar_format" }
    }
    /** Journal intent before a profile transaction; a rollback still retains its referenced photo. */
    @Synchronized internal fun queueCleanup(references: Set<String>) {
        val candidates=references.filter { it.isNotEmpty() }.onEach(::file).toSet()
        if(candidates.isEmpty()) return
        val pending=cleanup.getStringSet("pending",emptySet()).orEmpty()+candidates
        check(cleanup.edit().putStringSet("pending",pending).commit()) { "avatar_cleanup_pending" }
    }
    /** Never sweep unrelated drafts; unsuccessful deletes remain queued across process restarts. */
    @Synchronized internal fun finishCleanup(references: Set<String>) {
        val pending=cleanup.getStringSet("pending",emptySet()).orEmpty().toSet()
        if(pending.isEmpty()) return
        val remaining=(pending-references).filter { reference -> val target=file(reference); target.exists() && !target.delete() }.toSet()
        check(cleanup.edit().putStringSet("pending",remaining).commit()) { "avatar_cleanup_pending" }
    }
    companion object { fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
}
