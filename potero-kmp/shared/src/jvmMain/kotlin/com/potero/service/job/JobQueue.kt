package com.potero.service.job

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Job status enum
 */
enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Job type enum for different background tasks
 */
enum class JobType {
    PDF_ANALYSIS,
    PDF_REANALYSIS,
    AUTO_TAGGING,
    METADATA_LOOKUP,
    TAG_MERGE,
    BULK_IMPORT,
    THUMBNAIL_GENERATION
}

/**
 * Represents a background job
 */
@Serializable
data class Job(
    val id: String,
    val type: JobType,
    val title: String,
    val description: String = "",
    val status: JobStatus = JobStatus.PENDING,
    val progress: Int = 0, // 0-100
    val progressMessage: String = "",
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val error: String? = null,
    val result: String? = null, // JSON string for job-specific results
    val paperId: String? = null // Associated paper if applicable
)

/**
 * Job queue for managing background tasks
 * Uses coroutines for async execution and provides real-time status updates
 */
class JobQueue(
    private val maxConcurrentJobs: Int = 3,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _jobsFlow = MutableStateFlow<List<Job>>(emptyList())
    val jobsFlow: StateFlow<List<Job>> = _jobsFlow.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val mutex = Mutex()

    // Keep completed jobs for a while before cleanup
    private val completedJobRetentionMs = 5 * 60 * 1000L // 5 minutes

    /**
     * Submit a new job to the queue
     */
    suspend fun submitJob(
        type: JobType,
        title: String,
        description: String = "",
        paperId: String? = null,
        task: suspend (JobContext) -> String?
    ): Job {
        val job = Job(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            description = description,
            createdAt = Clock.System.now(),
            paperId = paperId
        )

        jobs[job.id] = job
        updateJobsFlow()

        // Start the job
        startJob(job, task)

        return job
    }

    private fun startJob(job: Job, task: suspend (JobContext) -> String?) {
        val coroutineJob = scope.launch {
            val context = JobContextImpl(job.id)

            try {
                // Update to running
                updateJob(job.id) { it.copy(
                    status = JobStatus.RUNNING,
                    startedAt = Clock.System.now()
                )}

                // Execute the task
                val result = task(context)

                // Mark as completed
                updateJob(job.id) { it.copy(
                    status = JobStatus.COMPLETED,
                    progress = 100,
                    completedAt = Clock.System.now(),
                    result = result
                )}

            } catch (e: CancellationException) {
                updateJob(job.id) { it.copy(
                    status = JobStatus.CANCELLED,
                    completedAt = Clock.System.now()
                )}
            } catch (e: Exception) {
                updateJob(job.id) { it.copy(
                    status = JobStatus.FAILED,
                    completedAt = Clock.System.now(),
                    error = e.message ?: "Unknown error"
                )}
            } finally {
                runningJobs.remove(job.id)
                scheduleCleanup(job.id)
            }
        }

        runningJobs[job.id] = coroutineJob
    }

    /**
     * Update job progress
     */
    private inner class JobContextImpl(private val jobId: String) : JobContext {
        override suspend fun updateProgress(progress: Int, message: String) {
            updateJob(jobId) { it.copy(
                progress = progress.coerceIn(0, 100),
                progressMessage = message
            )}
        }

        override fun isCancelled(): Boolean {
            return jobs[jobId]?.status == JobStatus.CANCELLED
        }
    }

    private suspend fun updateJob(jobId: String, update: (Job) -> Job) {
        mutex.withLock {
            jobs[jobId]?.let { current ->
                jobs[jobId] = update(current)
                updateJobsFlow()
            }
        }
    }

    private fun updateJobsFlow() {
        _jobsFlow.value = jobs.values
            .sortedByDescending { it.createdAt }
            .toList()
    }

    /**
     * Get all jobs
     */
    fun getAllJobs(): List<Job> = jobs.values.sortedByDescending { it.createdAt }

    /**
     * Get active jobs (pending or running)
     */
    fun getActiveJobs(): List<Job> = jobs.values
        .filter { it.status == JobStatus.PENDING || it.status == JobStatus.RUNNING }
        .sortedByDescending { it.createdAt }

    /**
     * Get job by ID
     */
    fun getJob(jobId: String): Job? = jobs[jobId]

    /**
     * Cancel a job
     */
    suspend fun cancelJob(jobId: String): Boolean {
        val coroutineJob = runningJobs[jobId]
        if (coroutineJob != null) {
            coroutineJob.cancel()
            updateJob(jobId) { it.copy(status = JobStatus.CANCELLED) }
            return true
        }

        // If pending, just mark as cancelled
        val job = jobs[jobId]
        if (job?.status == JobStatus.PENDING) {
            updateJob(jobId) { it.copy(status = JobStatus.CANCELLED) }
            return true
        }

        return false
    }

    /**
     * Clear completed/failed/cancelled jobs
     */
    fun clearCompletedJobs() {
        val toRemove = jobs.values
            .filter { it.status in listOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED) }
            .map { it.id }

        toRemove.forEach { jobs.remove(it) }
        updateJobsFlow()
    }

    private fun scheduleCleanup(jobId: String) {
        scope.launch {
            delay(completedJobRetentionMs)
            jobs.remove(jobId)
            updateJobsFlow()
        }
    }

    /**
     * Shutdown the job queue
     */
    fun shutdown() {
        scope.cancel()
        runningJobs.clear()
        jobs.clear()
    }
}

/**
 * Context for job execution - allows updating progress
 */
interface JobContext {
    suspend fun updateProgress(progress: Int, message: String = "")
    fun isCancelled(): Boolean
}

/**
 * Singleton job queue instance
 */
object GlobalJobQueue {
    val instance: JobQueue by lazy { JobQueue() }
}
