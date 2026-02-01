package com.example.soprintsgr.ui.tasks

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
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
        val tvAsesor: TextView = view.findViewById(R.id.tvAsesor)
        val tvFechaLimite: TextView = view.findViewById(R.id.tvFechaLimite)
        val layoutTiempoEjecucion: View = view.findViewById(R.id.layoutTiempoEjecucion)
        val tvTiempoEjecucion: TextView = view.findViewById(R.id.tvTiempoEjecucion)
        val ivOverdueAlert: ImageView = view.findViewById(R.id.ivOverdueAlert)
        var pulseAnimator: ObjectAnimator? = null
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
        holder.tvAsesor.text = holder.itemView.context.getString(R.string.asesor_full_name, task.asesorCrea.nombre, task.asesorCrea.apellido)
        holder.tvEstado.text = task.estadoTarea.nombre
        
        // Format date
        var fechaLimiteDate: Date? = null
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            fechaLimiteDate = inputFormat.parse(task.fechaLimite)
            holder.tvFechaLimite.text = fechaLimiteDate?.let { outputFormat.format(it) } ?: task.fechaLimite
        } catch (_: Exception) {
            holder.tvFechaLimite.text = task.fechaLimite
        }
        
        // Set estado background color
        holder.tvEstado.setBackgroundColor(getEstadoColor(task.estadoTarea.nombre))

        // Execution time logic
        if (!task.tiempoEjecucion.isNullOrEmpty()) {
            holder.layoutTiempoEjecucion.visibility = View.VISIBLE
            holder.tvTiempoEjecucion.text = holder.itemView.context.getString(R.string.execution_time_minutes, task.tiempoEjecucion)
        } else {
            holder.layoutTiempoEjecucion.visibility = View.GONE
        }
        
        // Check if task is overdue - only show for non-completed tasks
        val isCompleted = task.estadoTarea.nombre.uppercase() == "COMPLETADA"
        val isOverdue = isTaskOverdue(fechaLimiteDate) && !isCompleted
        
        if (isOverdue) {
            holder.ivOverdueAlert.visibility = View.VISIBLE
            holder.tvFechaLimite.setTextColor(Color.parseColor("#D32F2F"))
            
            // Start pulse animation
            if (holder.pulseAnimator == null || !holder.pulseAnimator!!.isRunning) {
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f)
                val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.7f, 1f)
                
                holder.pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    holder.ivOverdueAlert, scaleX, scaleY, alpha
                ).apply {
                    duration = 1000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        } else {
            holder.ivOverdueAlert.visibility = View.GONE
            holder.tvFechaLimite.setTextColor(
                holder.itemView.context.getColor(R.color.task_text_primary)
            )
            
            // Stop animation
            holder.pulseAnimator?.cancel()
            holder.pulseAnimator = null
        }
        
        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }

    override fun onViewRecycled(holder: TaskViewHolder) {
        super.onViewRecycled(holder)
        // Clean up animation when view is recycled
        holder.pulseAnimator?.cancel()
        holder.pulseAnimator = null
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    fun submitList(newTasks: List<Task>) {
        updateTasks(newTasks)
    }

    private fun isTaskOverdue(fechaLimite: Date?): Boolean {
        if (fechaLimite == null) return false
        return fechaLimite.before(Date())
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
