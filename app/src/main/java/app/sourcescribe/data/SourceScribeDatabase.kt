package app.sourcescribe.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.ExportState
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import kotlinx.coroutines.flow.Flow

internal fun decodeStoredJobConfig(raw: String): app.sourcescribe.core.JobConfig? = try {
    kotlinx.serialization.json.Json.decodeFromString<app.sourcescribe.core.JobConfig>(raw)
} catch (_: IllegalArgumentException) {
    null
}

@Entity(tableName = "sources")
data class SourceRow(@PrimaryKey val id: String, val snapshot: String, val title: String, val importedPath: String? = null)

@Entity(tableName = "jobs", foreignKeys = [ForeignKey(entity = SourceRow::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("sourceId")])
data class JobRow(
    @PrimaryKey val id: String,
    val sourceId: String,
    val config: String,
    val createdAt: Long,
    val state: ExecutionState = ExecutionState.QUEUED,
    val outcome: Outcome = Outcome.NONE,
    val cancelRequested: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val deleteRequested: Boolean = false,
)

@Entity(tableName = "attempts", foreignKeys = [ForeignKey(entity = JobRow::class, parentColumns = ["id"], childColumns = ["jobId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("jobId"), Index(value = ["jobId", "branch", "number"], unique = true), Index(value = ["state", "nextAt"])])
data class AttemptRow(
    @PrimaryKey val id: String,
    val jobId: String,
    val branch: Branch,
    val number: Int,
    val createdAt: Long,
    val state: ExecutionState = ExecutionState.QUEUED,
    val phase: Phase = Phase.RESOLVE,
    val outcome: Outcome = Outcome.NONE,
    val checkpoint: String = "{}",
    val engineId: String? = null,
    val nextAt: Long = 0,
    val retries: Int = 0,
    val error: String? = null,
    val leaseOwner: String? = null,
    val leaseUntil: Long = 0,
    val processedBytes: Long = 0,
    val totalBytes: Long? = null,
)

@Entity(tableName = "artifacts", foreignKeys = [ForeignKey(entity = AttemptRow::class, parentColumns = ["id"], childColumns = ["attemptId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("attemptId"), Index("jobId")])
data class ArtifactRow(
    @PrimaryKey val id: String,
    val jobId: String,
    val attemptId: String,
    val branch: Branch,
    val createdAt: Long,
    val sha256: String,
    val bytes: Long,
    val language: String?,
    val providerModel: String?,
    val complete: Boolean?,
    val warningCount: Int,
    /** Optional user-chosen export file stem; null keeps the generated name. */
    val displayName: String? = null,
)

@Entity(tableName = "submissions", foreignKeys = [ForeignKey(entity = AttemptRow::class, parentColumns = ["id"], childColumns = ["attemptId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("attemptId"), Index(value = ["attemptId", "chunkIndex"], unique = true)])
data class SubmissionRow(
    @PrimaryKey val id: String,
    val attemptId: String,
    val chunkIndex: Int,
    val provider: String,
    val credentialId: String,
    val region: String,
    val inputHash: String,
    val configHash: String,
    val state: SubmissionState,
    val createdAt: Long,
    val estimatedMicrousd: Long?,
    val remoteId: String? = null,
    val rawResponsePath: String? = null,
    val reusedFromId: String? = null,
    val rejectionCode: String? = null,
)

enum class SubmissionState { PREPARED, SENDING, ACCEPTED, RESPONSE_SAVED, UNCERTAIN, REJECTED, REMOTE_DELETED }

@Entity(tableName = "exports", foreignKeys = [ForeignKey(entity = ArtifactRow::class, parentColumns = ["id"], childColumns = ["artifactId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("artifactId")])
data class ExportRow(
    @PrimaryKey val id: String,
    val artifactId: String,
    val format: String,
    val treeUri: String,
    val createdAt: Long,
    val state: ExportState = ExportState.PENDING,
    val documentUri: String? = null,
    val verification: String? = null,
    val error: String? = null,
)

@Entity(tableName = "resource_leases")
data class ResourceLease(@PrimaryKey val name: String, val owner: String, val until: Long)

@Entity(tableName = "engines")
data class EngineRow(@PrimaryKey val id: String, val version: String, val ejsVersion: String, val channel: String, val state: String, val installedAt: Long)

/** One fallback per explicit caption attempt, independent of wall-clock changes. */
internal fun fallbackAttemptId(parentId: String): String =
    java.util.UUID.nameUUIDFromBytes("fallback:$parentId".toByteArray(Charsets.UTF_8)).toString()

@Dao
abstract class SourceScribeDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC") abstract fun observeJobs(): Flow<List<JobRow>>
    @Query("SELECT * FROM sources") abstract fun observeSources(): Flow<List<SourceRow>>
    @Query("SELECT * FROM attempts ORDER BY createdAt") abstract fun observeAttempts(): Flow<List<AttemptRow>>
    @Query("SELECT * FROM artifacts ORDER BY createdAt DESC") abstract fun observeArtifacts(): Flow<List<ArtifactRow>>
    @Query("SELECT * FROM exports ORDER BY createdAt DESC") abstract fun observeExports(): Flow<List<ExportRow>>
    @Query("SELECT DISTINCT attempts.jobId FROM submissions INNER JOIN attempts ON attempts.id = submissions.attemptId WHERE submissions.provider = 'ASSEMBLYAI' AND submissions.remoteId IS NOT NULL AND submissions.state != 'REMOTE_DELETED'")
    abstract fun observeRemoteDeletionJobIds(): Flow<List<String>>
    @Query("SELECT * FROM jobs WHERE id = :id") abstract suspend fun job(id: String): JobRow?
    @Query("SELECT * FROM jobs") abstract suspend fun allJobs(): List<JobRow>
    @Query("SELECT * FROM attempts") abstract suspend fun allAttempts(): List<AttemptRow>
    @Query("SELECT * FROM sources WHERE NOT EXISTS (SELECT 1 FROM jobs WHERE jobs.sourceId = sources.id)") abstract suspend fun unreferencedSources(): List<SourceRow>
    @Query("DELETE FROM resource_leases") abstract suspend fun clearResourceLeases()

    @Query("SELECT * FROM jobs WHERE deleteRequested = 1") abstract suspend fun pendingDeletion(): List<JobRow>
    @Query("SELECT COUNT(*) FROM jobs WHERE sourceId = :id") abstract suspend fun sourceReferences(id: String): Int
    @Query("SELECT * FROM jobs WHERE sourceId = :id") abstract suspend fun jobsForSource(id: String): List<JobRow>
    @Query("DELETE FROM exports WHERE artifactId IN (SELECT id FROM artifacts WHERE jobId = :id)") protected abstract suspend fun deleteExports(id: String)
    @Query("DELETE FROM artifacts WHERE jobId = :id") protected abstract suspend fun deleteArtifacts(id: String)
    @Query("DELETE FROM submissions WHERE attemptId IN (SELECT id FROM attempts WHERE jobId = :id)") protected abstract suspend fun deleteSubmissions(id: String)
    @Query("DELETE FROM attempts WHERE jobId = :id") protected abstract suspend fun deleteAttempts(id: String)
    @Query("DELETE FROM jobs WHERE id = :id") protected abstract suspend fun deleteJobRow(id: String)
    @Query("DELETE FROM sources WHERE id = :id AND NOT EXISTS (SELECT 1 FROM jobs WHERE sourceId = :id)") abstract suspend fun deleteUnusedSource(id: String)
    @Query("SELECT * FROM sources WHERE id = :id") abstract suspend fun source(id: String): SourceRow?
    @Query("SELECT * FROM attempts WHERE id = :id") abstract suspend fun attempt(id: String): AttemptRow?
    @Query("SELECT * FROM attempts WHERE jobId = :jobId ORDER BY createdAt") abstract suspend fun attempts(jobId: String): List<AttemptRow>
    @Query("SELECT * FROM attempts WHERE state = 'RUNNING'") abstract suspend fun interrupted(): List<AttemptRow>
    @Query("SELECT DISTINCT attempts.* FROM attempts INNER JOIN artifacts ON artifacts.attemptId = attempts.id WHERE attempts.state = 'FINISHED'") abstract suspend fun completedWithArtifacts(): List<AttemptRow>
    @Query("SELECT * FROM attempts WHERE state IN ('QUEUED','WAITING_NETWORK','WAITING_RATE_LIMIT','WAITING_REMOTE') AND nextAt <= :now ORDER BY nextAt,createdAt") abstract suspend fun due(now: Long): List<AttemptRow>
    @Query("SELECT * FROM attempts WHERE state IN ('QUEUED','WAITING_NETWORK','WAITING_RATE_LIMIT','WAITING_REMOTE') ORDER BY nextAt,createdAt") abstract suspend fun scheduled(): List<AttemptRow>
    @Query("SELECT * FROM artifacts WHERE id = :id") abstract suspend fun artifact(id: String): ArtifactRow?
    @Query("SELECT * FROM artifacts WHERE jobId = :jobId") abstract suspend fun artifacts(jobId: String): List<ArtifactRow>
    @Query("UPDATE artifacts SET displayName = :name WHERE id = :id") abstract suspend fun renameArtifact(id: String, name: String?): Int
    @Query("SELECT * FROM submissions WHERE attemptId = :attemptId ORDER BY chunkIndex") abstract suspend fun submissions(attemptId: String): List<SubmissionRow>
    @Query("SELECT submissions.* FROM submissions INNER JOIN attempts ON submissions.attemptId = attempts.id WHERE attempts.jobId = :jobId") abstract suspend fun submissionsForJob(jobId: String): List<SubmissionRow>
    @Query("SELECT * FROM exports WHERE artifactId = :artifactId ORDER BY createdAt DESC") abstract suspend fun exports(artifactId: String): List<ExportRow>
    @Query("SELECT * FROM exports") abstract suspend fun allExports(): List<ExportRow>
    @Query("SELECT * FROM exports WHERE id = :id") abstract suspend fun export(id: String): ExportRow?
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertSource(row: SourceRow): Long
    @Update abstract suspend fun updateSource(row: SourceRow)
    @Insert abstract suspend fun insertJob(row: JobRow)
    @Insert abstract suspend fun insertAttempt(row: AttemptRow)
    @Insert abstract suspend fun insertArtifact(row: ArtifactRow)
    @Insert abstract suspend fun insertSubmission(row: SubmissionRow)
    @Insert abstract suspend fun insertExport(row: ExportRow)
    @Update abstract suspend fun updateJob(row: JobRow)
    @Update abstract suspend fun updateAttempt(row: AttemptRow)
    @Update abstract suspend fun updateSubmission(row: SubmissionRow)
    @Update abstract suspend fun updateExport(row: ExportRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun recordEngine(row: EngineRow)
    @Query("SELECT * FROM jobs WHERE sourceId = :sourceId ORDER BY createdAt DESC LIMIT 1") abstract suspend fun latestJob(sourceId: String): JobRow?

    @Transaction
    open suspend fun createJob(source: SourceRow, job: JobRow, attempts: List<AttemptRow>) {
        insertSource(source)
        insertJob(job)
        attempts.forEach { insertAttempt(it) }
    }

    @Transaction
    open suspend fun finishDeletion(id: String, keepSource: Boolean = false) {
        val current = job(id) ?: return
        check(current.deleteRequested && current.cancelRequested)
        deleteExports(id)
        deleteArtifacts(id)
        deleteSubmissions(id)
        deleteAttempts(id)
        deleteJobRow(id)
        if (!keepSource) deleteUnusedSource(current.sourceId)
    }

    @Transaction
    open suspend fun requestDeletion(id: String): Boolean {
        if (job(id) == null) return false
        cancelJob(id)
        val cancelled = requireNotNull(job(id))
        updateJob(cancelled.copy(deleteRequested = true, cancelRequested = true,
            state = ExecutionState.CANCELLED, outcome = Outcome.CANCELLED))
        return true
    }

    @Transaction
    open suspend fun cancelJob(id: String): Boolean {
        val job = job(id) ?: return false
        val active = attempts(id).filter { it.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) }
        if (job.cancelRequested || active.isEmpty()) return false
        updateJob(job.copy(cancelRequested = true, state = ExecutionState.CANCELLED, outcome = Outcome.CANCELLED))
        for (row in active) {
            val pending = submissions(row.id).map { it.state }
            val uncertain = pending.any { it in setOf(SubmissionState.SENDING, SubmissionState.UNCERTAIN) }
            val remote = uncertain || SubmissionState.ACCEPTED in pending
            updateAttempt(row.copy(state = if (uncertain) ExecutionState.SUBMISSION_UNCERTAIN else ExecutionState.CANCELLED,
                outcome = Outcome.CANCELLED, error = if (remote) "REMOTE_MAY_CONTINUE" else row.error,
                leaseOwner = null, leaseUntil = 0))
            // The running worker releases resources after its native child has terminated.
        }
        return true
    }

    @Transaction
    open suspend fun insertFallback(parent: AttemptRow, owner: String, child: AttemptRow, now: Long = System.currentTimeMillis()): Boolean {
        val parentJob = job(parent.jobId) ?: return false
        val held = attempt(parent.id) ?: return false
        if (parentJob.cancelRequested || parentJob.deleteRequested || held.leaseOwner != owner || held.leaseUntil <= now ||
            attempt(fallbackAttemptId(parent.id)) != null) return false
        require(child.id == fallbackAttemptId(parent.id) && child.jobId == parent.jobId && child.branch == Branch.STT)
        insertAttempt(child)
        return true
    }

    @Query("SELECT COUNT(DISTINCT jobId) FROM attempts WHERE state = 'RUNNING' AND leaseUntil > :now") protected abstract suspend fun activeJobs(now: Long): Int
    @Query("SELECT COUNT(*) FROM attempts WHERE jobId = :jobId AND state = 'RUNNING' AND leaseUntil > :now") protected abstract suspend fun activeInJob(jobId: String, now: Long): Int
    @Query("UPDATE attempts SET state = 'RUNNING',leaseOwner = :owner,leaseUntil = :until WHERE id = :id AND state IN ('QUEUED','WAITING_NETWORK','WAITING_RATE_LIMIT','WAITING_REMOTE') AND nextAt <= :now AND leaseUntil <= :now") protected abstract suspend fun claimRow(id: String, owner: String, now: Long, until: Long): Int

    @Transaction
    open suspend fun claim(id: String, owner: String, now: Long, until: Long, parallelJobs: Int): AttemptRow? {
        require(parallelJobs in 1..4 && until > now)
        val row = attempt(id) ?: return null
        val currentJob = job(row.jobId) ?: return null
        if (currentJob.cancelRequested || currentJob.deleteRequested || activeJobs(now) >= parallelJobs || activeInJob(row.jobId, now) > 0) return null
        return if (claimRow(id, owner, now, until) == 1) attempt(id) else null
    }

    @Query("UPDATE attempts SET leaseOwner = NULL,leaseUntil = 0 WHERE id = :id AND leaseOwner = :owner") abstract suspend fun release(id: String, owner: String)
    @Query("UPDATE attempts SET nextAt = MAX(nextAt, :nextAt) WHERE id = :id AND state IN ('QUEUED','WAITING_NETWORK','WAITING_RATE_LIMIT','WAITING_REMOTE') AND leaseUntil <= :now") abstract suspend fun deferUnclaimed(id: String, now: Long, nextAt: Long)
    @Transaction
    open suspend fun saveClaimed(row: AttemptRow, owner: String): Boolean {
        val held = attempt(row.id) ?: return false
        if (held.leaseOwner != owner || held.leaseUntil <= System.currentTimeMillis()) return false
        updateAttempt(row)
        return true
    }
    @Query("UPDATE attempts SET state = 'WAITING_USER',error = 'SCHEDULING_FAILED',nextAt = 0 WHERE id = :id AND leaseOwner IS NULL AND state IN ('QUEUED','WAITING_NETWORK','WAITING_RATE_LIMIT','WAITING_REMOTE')")
    abstract suspend fun schedulingFailed(id: String): Int

    @Query("SELECT * FROM resource_leases WHERE name = :name") protected abstract suspend fun resource(name: String): ResourceLease?
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun putResource(lease: ResourceLease)
    @Query("DELETE FROM resource_leases WHERE owner = :owner") abstract suspend fun releaseResources(owner: String)
    @Transaction
    open suspend fun claimResource(name: String, owner: String, now: Long, until: Long): Boolean {
        val held = resource(name)
        if (held != null && held.owner != owner && held.until > now) return false
        putResource(ResourceLease(name, owner, until))
        return true
    }
}

class DatabaseTypes {
    @TypeConverter fun state(value: ExecutionState): String = value.name
    @TypeConverter fun state(value: String): ExecutionState = ExecutionState.valueOf(value)
    @TypeConverter fun phase(value: Phase): String = value.name
    @TypeConverter fun phase(value: String): Phase = Phase.valueOf(value)
    @TypeConverter fun outcome(value: Outcome): String = value.name
    @TypeConverter fun outcome(value: String): Outcome = Outcome.valueOf(value)
    @TypeConverter fun branch(value: Branch): String = value.name
    @TypeConverter fun branch(value: String): Branch = Branch.valueOf(value)
    @TypeConverter fun exportState(value: ExportState): String = value.name
    @TypeConverter fun exportState(value: String): ExportState = ExportState.valueOf(value)
    @TypeConverter fun submission(value: SubmissionState): String = value.name
    @TypeConverter fun submission(value: String): SubmissionState = SubmissionState.valueOf(value)
}

@Database(entities = [SourceRow::class, JobRow::class, AttemptRow::class, ArtifactRow::class, SubmissionRow::class, ExportRow::class, ResourceLease::class, EngineRow::class], version = 4, exportSchema = true)
@TypeConverters(DatabaseTypes::class)
abstract class SourceScribeDatabase : RoomDatabase() {
    abstract fun records(): SourceScribeDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artifacts ADD COLUMN displayName TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE submissions ADD COLUMN reusedFromId TEXT")
                db.execSQL("ALTER TABLE submissions ADD COLUMN rejectionCode TEXT")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN deleteRequested INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
