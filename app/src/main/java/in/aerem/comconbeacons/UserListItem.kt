package `in`.aerem.comconbeacons

import `in`.aerem.comconbeacons.models.UserResponse
import java.text.SimpleDateFormat
import java.util.*

class UserListItem {
    var id: Number
    var username: String
    var location: String
    var date: Date

    enum class Status {
        ADVENTURE,
        FREE,
        BUSY,
        UNKNOWN
    }

    lateinit var status: Status

    constructor(r: UserResponse) {
        id = r.id
        username = valueOr(r.name, "Anonymous")
        setStatusFromString(r.status)
        val l = r.location;
        location = l?.label ?: "None"
        date = getDate(r.updated_at)
    }

    fun setStatusFromString(s: String) {
        status =  when (s) {
            "adventure" -> Status.ADVENTURE
            "free" -> Status.FREE
            "busy" -> Status.BUSY
            else -> Status.UNKNOWN
        }
    }

    private var format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun valueOr(value: String?, defaultValue: String): String {
        return if (value == null || value.isEmpty()) defaultValue else value
    }

    private fun getDate(rawDate: String): Date {
        format.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        return format.parse(rawDate)
    }
}
