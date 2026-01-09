package com.example.soprintsgr.ui.tasks
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.soprintsgr.data.api.ArchivoAdjunto
import com.example.soprintsgr.R

class ArchivoAdjuntoAdapter(
    private val archivos: List<ArchivoAdjunto>,
    private val onArchivoClick: (ArchivoAdjunto) -> Unit
) : RecyclerView.Adapter<ArchivoAdjuntoAdapter.ArchivoViewHolder>() {

    class ArchivoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombreArchivo: TextView = view.findViewById(R.id.tvNombreArchivo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchivoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archivo_adjunto, parent, false)
        return ArchivoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArchivoViewHolder, position: Int) {
        val archivo = archivos[position]
        holder.tvNombreArchivo.text = archivo.nombreOriginal
        
        holder.itemView.setOnClickListener {
            onArchivoClick(archivo)
        }
    }

    override fun getItemCount() = archivos.size
}
