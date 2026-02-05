package com.example.soprintsgr.data

import android.content.Context
import android.content.SharedPreferences
import com.example.soprintsgr.data.api.Task
import com.google.gson.Gson

class ActiveTaskManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "active_task_prefs"
        private const val KEY_ACTIVE_TASK = "active_task"
        private const val KEY_START_TIME = "start_time"

        @Volatile
        private var instance: ActiveTaskManager? = null

        fun getInstance(context: Context): ActiveTaskManager {
            return instance ?: synchronized(this) {
                instance ?: ActiveTaskManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun startTask(task: Task, explicitStartTime: Long = 0L) {
        val taskJson = gson.toJson(task)
        
        // Use explicit start time if provided, otherwise perform logic
        val startTime = if (explicitStartTime > 0L) {
            explicitStartTime
        } else {
            // Only set start time if there isn't one already or if we want to overwrite
            val existingStartTime = getStartTime()
            if (existingStartTime > 0L) existingStartTime else System.currentTimeMillis()
        }
        
        prefs.edit().apply {
            putString(KEY_ACTIVE_TASK, taskJson)
            putLong(KEY_START_TIME, startTime)
            apply()
        }
    }

    // Update task data without changing the start time
    fun updateTaskData(task: Task) {
        val taskJson = gson.toJson(task)
        prefs.edit().apply {
            putString(KEY_ACTIVE_TASK, taskJson)
            apply()
        }
    }

    fun getActiveTask(): Task? {
        val taskJson = prefs.getString(KEY_ACTIVE_TASK, null) ?: return null
        return try {
            gson.fromJson(taskJson, Task::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getStartTime(): Long {
        return prefs.getLong(KEY_START_TIME, 0L)
    }

    fun getElapsedTime(): Long {
        val startTime = getStartTime()
        if (startTime == 0L) return 0L
        return System.currentTimeMillis() - startTime
    }

    fun clearActiveTask() {
        prefs.edit().apply {
            remove(KEY_ACTIVE_TASK)
            remove(KEY_START_TIME)
            apply()
        }
    }

    fun hasActiveTask(): Boolean {
        return getActiveTask() != null
    }
}
