package com.adarsh.hellomom.data.local

import androidx.room.TypeConverter
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.data.local.entity.ReminderType

class Converters {
    @TypeConverter
    fun fromReminderStatus(status: ReminderStatus): String {
        return status.name
    }

    @TypeConverter
    fun toReminderStatus(status: String): ReminderStatus {
        return try {
            when (status) {
                "DONE" -> ReminderStatus.COMPLETED
                "MISSED" -> ReminderStatus.EXPIRED
                else -> ReminderStatus.valueOf(status)
            }
        } catch (_: Exception) {
            ReminderStatus.PENDING
        }
    }

    @TypeConverter
    fun fromReminderType(type: ReminderType): String {
        return type.name
    }

    @TypeConverter
    fun toReminderType(type: String): ReminderType {
        return ReminderType.valueOf(type)
    }
}
