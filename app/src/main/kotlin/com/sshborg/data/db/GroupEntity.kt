package com.sshborg.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// "host_groups", not "groups": GROUPS is an SQLite keyword.
@Entity(tableName = "host_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color, one of [SWATCHES]. */
    val color: Int,
    /** Whether the group's section is collapsed in the host list. */
    val collapsed: Boolean = false,
    /** Position in the manual list order; null until that order is first seeded. */
    val position: Int? = null,
) {
    companion object {
        /** Predefined group colors, readable as icon tints on both light and dark surfaces. */
        val SWATCHES = listOf(
            0xFFE53935.toInt(), // red
            0xFFF57C00.toInt(), // orange
            0xFFF9A825.toInt(), // amber
            0xFF43A047.toInt(), // green
            0xFF00897B.toInt(), // teal
            0xFF039BE5.toInt(), // light blue
            0xFF5C6BC0.toInt(), // indigo
            0xFFAB47BC.toInt(), // purple
            0xFFEC407A.toInt(), // pink
            0xFF78909C.toInt(), // blue grey
        )
    }
}
