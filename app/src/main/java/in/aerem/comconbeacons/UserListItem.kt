package `in`.aerem.comconbeacons

import `in`.aerem.comconbeacons.models.UserResponse
import org.ocpsoft.prettytime.PrettyTime
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class UserListItem {
    var id: Number
    var username: String
    var location: String
    var time: String
    var status: String

    constructor(r: UserResponse) {
        id = r.id
        username = valueOr(r.name, "Anonymous")
        status = r.status
        val l = r.location;
        location = l?.label ?: "None"
        time = humanReadableDateInfo(r.updated_at)
    }

    private var format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun valueOr(value: String?, defaultValue: String): String {
        return if (value == null || value.isEmpty()) defaultValue else value
    }

    private fun humanReadableDateInfo(rawDate: String): String {
        format.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        try {
            val p = PrettyTime(Locale("ru"))
            val date = format.parse(rawDate)
            return p.format(date)
        } catch (e: ParseException) {
            return ""
        }
    }
}
