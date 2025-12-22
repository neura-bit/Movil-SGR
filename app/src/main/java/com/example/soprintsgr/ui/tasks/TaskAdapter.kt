package com.example.soprintsgr.ui.tasks

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.soprintsgr.R
import com.example.soprintsgr.data.api.Task
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvCliente: TextView = view.findViewById(R.id.tvCliente)
        val tvTipoOperacion: TextView = view.findViewById(R.id.tvTipoOperacion)
        val tvFechaLimite: TextView = view.findViewById(R.id.tvFechaLimite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        
        holder.tvNombre.text = task.nombre
        holder.tvCliente.text = task.cliente.nombre
        holder.tvTipoOperacion.text = task.tipoOperacion.nombre
        holder.tvEstado.text = task.estadoTarea.nombre
        
        // Format date
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(task.fechaLimite)
            holder.tvFechaLimite.text = date?.let { outputFormat.format(it) } ?: task.fechaLimite
        } catch (e: Exception) {
            holder.tvFechaLimite.text = task.fechaLimite
        }
        
        // Set estado background color
        holder.tvEstado.setBackgroundColor(getEstadoColor(task.estadoTarea.nombre))
        
        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    private fun getEstadoColor(estado: String): Int {
        return when (estado.uppercase()) {
            "CREADA" -> Color.parseColor("#4CAF50") // Green
            "EN_PROCESO", "EN PROCESO" -> Color.parseColor("#FF9800") // Orange
            "COMPLETADA" -> Color.parseColor("#2196F3") // Blue
            "CANCELADA" -> Color.parseColor("#9E9E9E") // Gray
            else -> Color.parseColor("#607D8B") // Default gray
        }
    }
}
