package tech.zakomi.zakdroid.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "recent_workspaces", primaryKeys = ["root_key"])
data class RecentWorkspaceEntity(
    @ColumnInfo(name = "root_key") val rootKey: String,
    val name: String,
    val kind: String,
    @ColumnInfo(name = "last_opened") val lastOpened: Long,
)

@Entity(tableName = "open_tabs", primaryKeys = ["workspace_id", "file_key"])
data class OpenTabEntity(
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "file_key") val fileKey: String,
    val name: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val position: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "cursor_line") val cursorLine: Int,
    @ColumnInfo(name = "cursor_column") val cursorColumn: Int,
)
