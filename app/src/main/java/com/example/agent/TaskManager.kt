package com.example.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Central TaskManager coordinating AI generation, TTS, agent workflows,
 * and cancellable network requests to prevent orphaned coroutines while
 * supporting granular cancellation.
 */
object TaskManager {

    private const val TAG = "TaskManager"
    const val TASK_TYPE_AI_GENERATION = "ai_generation"
    const val TASK_TYPE_TTS = "tts_playback"
    const val TASK_TYPE_AGENT_WORKFLOW = "agent_workflow"
    const val TASK_TYPE_NETWORK = "network_request"


    private val activeTasks = ConcurrentHashMap<String, Job>()

    @Volatile
    private var currentTurnJob: Job? = null

    /**
     * Registers the primary conversational turn job.
     * Safely cancels any preexisting turn job before assigning the new one.
     */
    fun registerCurrentTurnJob(job: Job) {
        val old = currentTurnJob
        if (old != null && old.isActive) {
            Log.d(TAG, "Cancelling prior active turn job for new turn")
            old.cancel(CancellationException("Superceded by new user turn"))
        }
        currentTurnJob = job
        job.invokeOnCompletion {
            if (currentTurnJob == job) {
                currentTurnJob = null
            }
        }
    }

    /**
     * Cancels the active conversational turn safely (AI generation + TTS + agent workflow).
     */
    fun cancelCurrentTask(reason: String = "User requested cancellation") {
        val job = currentTurnJob
        if (job != null && job.isActive) {
            Log.i(TAG, "Cancelling current task: $reason")
            job.cancel(CancellationException(reason))
            currentTurnJob = null
        }
        // Also cancel any registered agent workflow tasks
        cancelTasksByType(TASK_TYPE_AGENT_WORKFLOW, reason)
        cancelTasksByType(TASK_TYPE_AI_GENERATION, reason)
    }

    /**
     * Registers a named sub-task (e.g. background check, network fetch).
     */
    fun registerTask(taskId: String, job: Job) {
        activeTasks[taskId]?.cancel(CancellationException("Replaced by new task instance: $taskId"))
        activeTasks[taskId] = job
        job.invokeOnCompletion {
            activeTasks.remove(taskId, job)
        }
    }

    fun cancelTask(taskId: String, reason: String = "Explicit cancellation") {
        val job = activeTasks.remove(taskId)
        if (job != null && job.isActive) {
            Log.d(TAG, "Cancelling task '$taskId': $reason")
            job.cancel(CancellationException(reason))
        }
    }

    fun cancelTasksByType(typePrefix: String, reason: String = "Type cancellation") {
        activeTasks.forEach { (key, job) ->
            if (key.startsWith(typePrefix) && job.isActive) {
                job.cancel(CancellationException(reason))
                activeTasks.remove(key)
            }
        }
    }

    fun cancelAll(reason: String = "Global cancel all") {
        Log.w(TAG, "Global cancellation of all tasks: $reason")
        currentTurnJob?.cancel(CancellationException(reason))
        currentTurnJob = null

        activeTasks.values.forEach { job ->
            if (job.isActive) {
                job.cancel(CancellationException(reason))
            }
        }
        activeTasks.clear()
    }

    fun hasActiveTask(): Boolean {
        return (currentTurnJob?.isActive == true) || activeTasks.values.any { it.isActive }
    }
}
