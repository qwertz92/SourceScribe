package app.sourcescribe.data

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SourceScribeDatabase::class.java,
    )

    @Test
    fun migrateFromVersion1To4PreservesRowsAndAddsNullableColumns() {
        val databaseName = "migration-${UUID.randomUUID()}.db"
        helper.createDatabase(databaseName, 1).apply {
            execSQL("INSERT INTO sources(id,snapshot,title,importedPath) VALUES ('source-v1','snapshot-v1','Migration source',NULL)")
            execSQL("INSERT INTO jobs(id,sourceId,config,createdAt,state,outcome,cancelRequested) VALUES ('job-v1','source-v1','config-v1',11,'QUEUED','NONE',0)")
            execSQL("INSERT INTO attempts(id,jobId,branch,number,createdAt,state,phase,outcome,checkpoint,engineId,nextAt,retries,error,leaseOwner,leaseUntil,processedBytes,totalBytes) VALUES ('attempt-v1','job-v1','CAPTIONS',1,12,'FINISHED','PERSIST','SUCCESS','checkpoint-v1','engine-v1',13,0,NULL,NULL,0,100,100)")
            execSQL("INSERT INTO artifacts(id,jobId,attemptId,branch,createdAt,sha256,bytes,language,providerModel,complete,warningCount) VALUES ('artifact-v1','job-v1','attempt-v1','CAPTIONS',14,'sha-v1',100,'de','model-v1',1,2)")
            execSQL("INSERT INTO submissions(id,attemptId,chunkIndex,provider,credentialId,region,inputHash,configHash,state,createdAt,estimatedMicrousd,remoteId,rawResponsePath) VALUES ('submission-v1','attempt-v1',0,'GROQ','credential-v1','US','input-v1','config-hash-v1','RESPONSE_SAVED',15,42,'remote-v1','/private/response-v1.json')")
            execSQL("INSERT INTO exports(id,artifactId,format,treeUri,createdAt,state,documentUri,verification,error) VALUES ('export-v1','artifact-v1','MARKDOWN','content://fixture/tree',16,'EXPORTED','content://fixture/document','verified-v1',NULL)")
            execSQL("INSERT INTO resource_leases(name,owner,until) VALUES ('audio','owner-v1',17)")
            execSQL("INSERT INTO engines(id,version,ejsVersion,channel,state,installedAt) VALUES ('engine-v1','1.0.0','ejs-v1','stable','ACTIVE',18)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            SourceScribeDatabase.MIGRATION_1_2,
            SourceScribeDatabase.MIGRATION_2_3,
            SourceScribeDatabase.MIGRATION_3_4,
        )
        try {
            assertEquals(1, count(migrated, "sources"))
            assertEquals(1, count(migrated, "jobs"))
            assertEquals(1, count(migrated, "attempts"))
            assertEquals(1, count(migrated, "artifacts"))
            assertEquals(1, count(migrated, "submissions"))
            assertEquals(1, count(migrated, "exports"))
            assertEquals(1, count(migrated, "resource_leases"))
            assertEquals(1, count(migrated, "engines"))

            row(migrated, "SELECT id,snapshot,title,importedPath FROM sources WHERE id = 'source-v1'") { cursor ->
                assertEquals("source-v1", cursor.getString(0))
                assertEquals("snapshot-v1", cursor.getString(1))
                assertEquals("Migration source", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
            row(migrated, "SELECT id,sourceId,config,createdAt,state,outcome,cancelRequested,deleteRequested FROM jobs WHERE id = 'job-v1'") { cursor ->
                assertEquals("job-v1", cursor.getString(0))
                assertEquals("source-v1", cursor.getString(1))
                assertEquals("config-v1", cursor.getString(2))
                assertEquals(11L, cursor.getLong(3))
                assertEquals("QUEUED", cursor.getString(4))
                assertEquals("NONE", cursor.getString(5))
                assertEquals(0, cursor.getInt(6))
                assertEquals(0, cursor.getInt(7))
            }
            row(migrated, "SELECT id,jobId,branch,number,checkpoint FROM attempts WHERE id = 'attempt-v1'") { cursor ->
                assertEquals("attempt-v1", cursor.getString(0))
                assertEquals("job-v1", cursor.getString(1))
                assertEquals("CAPTIONS", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals("checkpoint-v1", cursor.getString(4))
            }
            row(migrated, "SELECT id,jobId,attemptId,sha256,bytes,language,complete,warningCount,displayName FROM artifacts WHERE id = 'artifact-v1'") { cursor ->
                assertEquals("artifact-v1", cursor.getString(0))
                assertEquals("job-v1", cursor.getString(1))
                assertEquals("attempt-v1", cursor.getString(2))
                assertEquals("sha-v1", cursor.getString(3))
                assertEquals(100L, cursor.getLong(4))
                assertEquals("de", cursor.getString(5))
                assertEquals(1, cursor.getInt(6))
                assertEquals(2, cursor.getInt(7))
                assertTrue("migrated artifacts keep no export name", cursor.isNull(8))
            }
            row(migrated, "SELECT id,attemptId,provider,credentialId,region,inputHash,configHash,state,estimatedMicrousd,remoteId,rawResponsePath,reusedFromId,rejectionCode FROM submissions WHERE id = 'submission-v1'") { cursor ->
                assertEquals("submission-v1", cursor.getString(0))
                assertEquals("attempt-v1", cursor.getString(1))
                assertEquals("GROQ", cursor.getString(2))
                assertEquals("credential-v1", cursor.getString(3))
                assertEquals("US", cursor.getString(4))
                assertEquals("input-v1", cursor.getString(5))
                assertEquals("config-hash-v1", cursor.getString(6))
                assertEquals("RESPONSE_SAVED", cursor.getString(7))
                assertEquals(42L, cursor.getLong(8))
                assertEquals("remote-v1", cursor.getString(9))
                assertEquals("/private/response-v1.json", cursor.getString(10))
                assertTrue(cursor.isNull(11))
                assertTrue(cursor.isNull(12))
            }
            row(migrated, "SELECT id,artifactId,format,treeUri,state,documentUri,verification FROM exports WHERE id = 'export-v1'") { cursor ->
                assertEquals("export-v1", cursor.getString(0))
                assertEquals("artifact-v1", cursor.getString(1))
                assertEquals("MARKDOWN", cursor.getString(2))
                assertEquals("content://fixture/tree", cursor.getString(3))
                assertEquals("EXPORTED", cursor.getString(4))
                assertEquals("content://fixture/document", cursor.getString(5))
                assertEquals("verified-v1", cursor.getString(6))
            }
            row(migrated, "SELECT name,owner,until FROM resource_leases WHERE name = 'audio'") { cursor ->
                assertEquals("audio", cursor.getString(0))
                assertEquals("owner-v1", cursor.getString(1))
                assertEquals(17L, cursor.getLong(2))
            }
            row(migrated, "SELECT id,version,ejsVersion,channel,state,installedAt FROM engines WHERE id = 'engine-v1'") { cursor ->
                assertEquals("engine-v1", cursor.getString(0))
                assertEquals("1.0.0", cursor.getString(1))
                assertEquals("ejs-v1", cursor.getString(2))
                assertEquals("stable", cursor.getString(3))
                assertEquals("ACTIVE", cursor.getString(4))
                assertEquals(18L, cursor.getLong(5))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrateFromVersion2To3PreservesSubmissionCostAndRemoteId() {
        val databaseName = "migration-${UUID.randomUUID()}.db"
        helper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO sources(id,snapshot,title,importedPath) VALUES ('source-v2','snapshot-v2','Migration source v2',NULL)")
            execSQL("INSERT INTO jobs(id,sourceId,config,createdAt,state,outcome,cancelRequested,deleteRequested) VALUES ('job-v2','source-v2','config-v2',21,'FINISHED','SUCCESS',0,0)")
            execSQL("INSERT INTO attempts(id,jobId,branch,number,createdAt,state,phase,outcome,checkpoint,engineId,nextAt,retries,error,leaseOwner,leaseUntil,processedBytes,totalBytes) VALUES ('attempt-v2','job-v2','STT',1,22,'FINISHED','PERSIST','SUCCESS','checkpoint-v2','engine-v2',23,0,NULL,NULL,0,200,200)")
            execSQL("INSERT INTO submissions(id,attemptId,chunkIndex,provider,credentialId,region,inputHash,configHash,state,createdAt,estimatedMicrousd,remoteId,rawResponsePath) VALUES ('submission-v2','attempt-v2',0,'ASSEMBLYAI','credential-v2','EU','input-v2','config-hash-v2','RESPONSE_SAVED',24,987,'remote-v2','/private/response-v2.json')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            SourceScribeDatabase.MIGRATION_2_3,
        )
        try {
            assertEquals(1, count(migrated, "sources"))
            assertEquals(1, count(migrated, "jobs"))
            assertEquals(1, count(migrated, "attempts"))
            assertEquals(1, count(migrated, "submissions"))
            row(migrated, "SELECT estimatedMicrousd,remoteId,rawResponsePath,reusedFromId,rejectionCode FROM submissions WHERE id = 'submission-v2'") { cursor ->
                assertEquals(987L, cursor.getLong(0))
                assertEquals("remote-v2", cursor.getString(1))
                assertEquals("/private/response-v2.json", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
        } finally {
            migrated.close()
        }
    }

    private fun count(database: SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT COUNT(*) FROM $table").let { cursor ->
            try {
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            } finally {
                cursor.close()
            }
        }

    private fun row(database: SupportSQLiteDatabase, query: String, check: (Cursor) -> Unit) {
        database.query(query).let { cursor ->
            try {
                assertTrue(cursor.moveToFirst())
                check(cursor)
            } finally {
                cursor.close()
            }
        }
    }
}
