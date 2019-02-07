package in.aerem.comconbeacons;

import in.aerem.comconbeacons.models.UsersResponse;
import org.ocpsoft.prettytime.PrettyTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class UserListItem {
    static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public String username;
    public String location;
    public String time;

    static private String valueOr(String value, String defaultValue) {
        if (value == null || value.isEmpty())
            return defaultValue;
        return value;
    }

    public UserListItem(UsersResponse r) {
        username = valueOr(r.getName(), r.getEmail());
        if (r.getBeacon() == null) {
            location = "None";
        } else {
            location = valueOr(r.getBeacon().getLabel(), r.getBeacon().getBssid());
        }
        time = humanReadableDateInfo(r.getUpdated_at());
    }

    private static String humanReadableDateInfo(String rawDate) {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            PrettyTime p = new PrettyTime(new Locale("ru"));
            Date date = format.parse(rawDate);
            return p.format(date);
        } catch (ParseException e) {
            return "";
        }
    }
}
