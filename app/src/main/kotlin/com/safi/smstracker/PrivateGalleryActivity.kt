package com.safi.smstracker

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class PrivateGalleryActivity : AppCompatActivity() {
    private val uris = mutableListOf<Uri>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rv = RecyclerView(this)
        setContentView(rv)
        title = "🖼️ Galerie Privée"
        loadPhotos()
        rv.adapter = Adapter(uris)
    }

    private fun loadPhotos() {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("%Pictures/SAFI_Private%"), null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) uris.add(Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idIdx).toString()
            ))
        }
    }

    inner class Adapter(private val list: List<Uri>) : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) { val img = v as ImageView }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val img = ImageView(p.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 350)
                setPadding(4,4,4,4)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF333333.toInt())
            }
            return VH(img)
        }
        override fun onBindViewHolder(h: VH, i: Int) { h.img.setImageURI(list[i]) }
        override fun getItemCount() = list.size
    }
}
