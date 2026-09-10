package app.sourcescribe.data

import android.content.Context
import androidx.room.Room
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.NativeRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SourceScribeDatabase =
        Room.databaseBuilder(context, SourceScribeDatabase::class.java, "sourcescribe.db")
            .addMigrations(SourceScribeDatabase.MIGRATION_1_2, SourceScribeDatabase.MIGRATION_2_3, SourceScribeDatabase.MIGRATION_3_4).build()

    @Provides fun records(database: SourceScribeDatabase): SourceScribeDao = database.records()
    @Provides @Singleton fun runtime(@ApplicationContext context: Context): NativeRuntime = NativeRuntime(context)
    @Provides @Singleton fun extractor(runtime: NativeRuntime): ExtractorEngine = ExtractorEngine(runtime)
    @Provides @Singleton fun engines(@ApplicationContext context: Context, runtime: NativeRuntime): EngineUpdateManager = EngineUpdateManager(context, runtime)
    @Provides @Singleton fun artifacts(@ApplicationContext context: Context): ArtifactFiles = ArtifactFiles(File(context.filesDir, "artifacts"))
    @Provides @Singleton fun credentials(@ApplicationContext context: Context): CredentialStore = CredentialStore(context)
    @Provides @Singleton fun providerHttp(): ProviderHttp = ProviderHttp()
}
