package com.safi.smstracker

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HiddenGalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhotoAdapter
    private val photoDir by lazy { File(filesDir, "received_photos") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_gallery)
        
        recyclerView = findViewById(R.id.galleryRecycler)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = PhotoAdapter()
        recyclerView.adapter = adapter
        
        findViewById<TextView>(R.id.btnCloseGallery).setOnClickListener { finish() }
        
        loadPhotos()
    }

    private fun loadPhotos() {
        if (!photoDir.exists()) photoDir.mkdirs()
        val photos = photoDir.listFiles { file -> 
            file.extension.lowercase() in listOf("jpg", "jpeg") 
        }?.sortedDescending() ?: emptyList()
        adapter.setPhotos(photos)
        
        findViewById<TextView>(R.id.galleryEmpty).visibility = 
            if (photos.isEmpty()) View.VISIBLE else View.GONE
    }

    inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.PhotoVH>() {
        private var photos = listOf<File>()

        fun setPhotos(list: List<File>) {
            photos = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_gallery_item, parent, false)
            return PhotoVH(view)
        }

        override fun onBindViewHolder(holder: PhotoVH, position: Int) {
            holder.bind(photos[position])
        }

        override fun getItemCount() = photos.size

        inner class PhotoVH(view: View) : RecyclerView.ViewHolder(view) {
            private val img: ImageView = view.findViewById(android.R.id.icon)
            
            fun bind(file: File) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                img.setImageBitmap(bmp)
                img.setOnClickListener {
                    AlertDialog.Builder(this@HiddenGalleryActivity)
                        .setTitle(file.name)
                        .setMessage("Supprimer cette photo ?")
                        .setPositiveButton("Supprimer") { _, _ ->
                            file.delete()
                            loadPhotos()
                        }
                        .setNegativeButton("Fermer", null)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPhotos()
    }
}
