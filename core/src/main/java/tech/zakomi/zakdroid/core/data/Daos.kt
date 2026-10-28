package tech.zakomi.zakdroid.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentWorkspaceDao {
    @Query("SELECT * FROM recent_workspaces ORDER BY last_opened DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentWorkspaceEntity>>

    @Upsert
    suspend fun upsert(entity: RecentWorkspaceEntity)

    @Query("DELETE FROM recent_workspaces WHERE root_key = :rootKey")
    suspend fun delete(rootKey: String)
}

@Dao
interface OpenTabDao {
    @Query("SELECT * FROM open_tabs WHERE workspace_id = :workspaceId ORDER BY position")
    suspend fun tabsFor(workspaceId: String): List<OpenTabEntity>

    @Query("DELETE FROM open_tabs WHERE workspace_id = :workspaceId")
    suspend fun clear(workspaceId: String)

    @Upsert
    suspend fun upsertAll(tabs: List<OpenTabEntity>)

    @Transaction
    suspend fun replaceAll(workspaceId: String, tabs: List<OpenTabEntity>) {
        clear(workspaceId)
        upsertAll(tabs)
    }
}
