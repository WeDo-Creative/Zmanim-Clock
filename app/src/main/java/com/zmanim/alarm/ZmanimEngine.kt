package com.zmanim.alarm

import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.util.*

enum class ZmanimMinhag(val displayName: String) {
    CHABAD("חב״ד (בעל התניא)"),
    ASHKENAZ("אשכנזים (גר״א)"),
    SEPHARDI("ספרדים ועדות המזרח"),
    CHASSIDIC("חסידים"),
    YEMENITE("תימנים"),
    CUSTOM("התאמה אישית")
}

enum class ZmanEvent(val title: String) {
    ALOT_HASHACHAR("עלות השחר"),
    MISHEYAKIR("משיכיר"),
    SUNRISE("זריחה (הנץ)"),
    SOF_ZMAN_SHMA("סוף זמן ק״ש"),
    SOF_ZMAN_TEFILLAH("סוף זמן תפילה"),
    CHATZOT("חצות היום"),
    MINCHA_GEDOLAH("מנחה גדולה"),
    PLAG_HAMINCHA("פלג המנחה"),
    SUNSET("שקיעה"),
    TZEIT_HAKOCHAVIM("צאת הכוכבים"),
    TZEIT_RT("צאת (ר״ת)")
}

enum class CalculationBasis(val label: String) {
    DEGREES("מעלות"),
    FIXED_MINUTES("דקות קבועות"),
    ZMANIYOT_MINUTES("דקות זמניות")
}

data class CustomZmanRule(val basis: CalculationBasis, val value: Double)
data class CityLocation(val name: String, val lat: Double, val lon: Double, val elevation: Double)

val ISRAEL_CITIES = listOf(
    CityLocation("ירושלים", 31.7683, 35.2137, 800.0),
    CityLocation("תל אביב", 32.0853, 34.7818, 15.0),
    CityLocation("חיפה", 32.7940, 34.9896, 200.0),
    CityLocation("אילת", 29.5577, 34.9519, 20.0),
    CityLocation("באר שבע", 31.2530, 34.7915, 280.0),
    CityLocation("צפת", 32.9646, 35.4960, 900.0),
    CityLocation("טבריה", 32.7922, 35.5312, 0.0),
    CityLocation("אשדוד", 31.8044, 34.6553, 20.0),
    CityLocation("בני ברק", 32.0849, 34.8352, 30.0),
    CityLocation("מודיעין", 31.8903, 35.0104, 260.0),
    CityLocation("כפר חב״ד", 31.9922, 34.8511, 40.0),
    CityLocation("קריית שמונה", 33.2073, 35.5721, 180.0)
)

data class ZmanimSettings(
    val city: CityLocation = ISRAEL_CITIES[0],
    val minhag: ZmanimMinhag = ZmanimMinhag.SEPHARDI,
    val useElevation: Boolean = false,
    val customAlot: CustomZmanRule = CustomZmanRule(CalculationBasis.DEGREES, 16.1),
    val customTzeit: CustomZmanRule = CustomZmanRule(CalculationBasis.DEGREES, 8.5),
    val themeMode: String = "system",
    val defaultRingtoneId: String = "system",
    val customRingtoneUri: String? = null,
    val widgetColorHex: Long = 0xFFFFFFFFL,
    val widgetAlpha: Float = 0.88f
)

object ZmanimEngine {
    fun calculateZmanTime(date: Calendar, event: ZmanEvent, offsetMinutes: Int, settings: ZmanimSettings): Calendar? {
        val tz = TimeZone.getTimeZone("Asia/Jerusalem")
        val elev = if (settings.useElevation) settings.city.elevation else 0.0
        val geo = GeoLocation(settings.city.name, settings.city.lat, settings.city.lon, elev, tz)
        val cal = ComplexZmanimCalendar(geo).apply {
            calendar = date.clone() as Calendar
            isUseElevation = settings.useElevation
        }

        val baseDate: Date? = when (settings.minhag) {
            ZmanimMinhag.CHABAD -> when (event) {
                ZmanEvent.ALOT_HASHACHAR -> cal.alosBaalHatanya
                ZmanEvent.MISHEYAKIR -> cal.misheyakir10Point2Degrees
                ZmanEvent.SUNRISE -> cal.getSunriseOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + 1.583)
                ZmanEvent.SOF_ZMAN_SHMA -> cal.sofZmanShmaBaalHatanya
                ZmanEvent.SOF_ZMAN_TEFILLAH -> cal.sofZmanTfilaBaalHatanya
                ZmanEvent.CHATZOT -> cal.chatzos
                ZmanEvent.MINCHA_GEDOLAH -> cal.minchaGedolaBaalHatanya
                ZmanEvent.PLAG_HAMINCHA -> cal.plagHaminchaBaalHatanya
                ZmanEvent.SUNSET -> cal.getSunsetOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + 1.583)
                ZmanEvent.TZEIT_HAKOCHAVIM -> cal.tzaisBaalHatanya
                ZmanEvent.TZEIT_RT -> cal.tzais72
            }
            ZmanimMinhag.ASHKENAZ, ZmanimMinhag.CHASSIDIC -> when (event) {
                ZmanEvent.ALOT_HASHACHAR -> cal.alos72
                ZmanEvent.MISHEYAKIR -> cal.misheyakir10Point2Degrees
                ZmanEvent.SUNRISE -> cal.sunrise
                ZmanEvent.SOF_ZMAN_SHMA -> cal.sofZmanShmaGRA
                ZmanEvent.SOF_ZMAN_TEFILLAH -> cal.sofZmanTfilaGRA
                ZmanEvent.CHATZOT -> cal.chatzos
                ZmanEvent.MINCHA_GEDOLAH -> cal.minchaGedola
                ZmanEvent.PLAG_HAMINCHA -> cal.plagHamincha
                ZmanEvent.SUNSET -> cal.sunset
                ZmanEvent.TZEIT_HAKOCHAVIM -> cal.tzais
                ZmanEvent.TZEIT_RT -> cal.tzais72
            }
            ZmanimMinhag.SEPHARDI -> when (event) {
                ZmanEvent.ALOT_HASHACHAR -> cal.alos72Zmanis
                ZmanEvent.MISHEYAKIR -> cal.misheyakir10Point2Degrees
                ZmanEvent.SUNRISE -> cal.sunrise
                ZmanEvent.SOF_ZMAN_SHMA -> cal.sofZmanShmaMGA72MinutesZmanis
                ZmanEvent.SOF_ZMAN_TEFILLAH -> cal.sofZmanTfilaMGA72MinutesZmanis
                ZmanEvent.CHATZOT -> cal.chatzos
                ZmanEvent.MINCHA_GEDOLAH -> cal.minchaGedola
                ZmanEvent.PLAG_HAMINCHA -> cal.plagHamincha
                ZmanEvent.SUNSET -> cal.sunset
                ZmanEvent.TZEIT_HAKOCHAVIM -> cal.tzaisGeonim5Point95Degrees
                ZmanEvent.TZEIT_RT -> cal.tzais72Zmanis
            }
            ZmanimMinhag.YEMENITE -> when (event) {
                ZmanEvent.ALOT_HASHACHAR -> cal.alos72
                ZmanEvent.MISHEYAKIR -> cal.misheyakir10Point2Degrees
                ZmanEvent.SUNRISE -> cal.sunrise
                ZmanEvent.SOF_ZMAN_SHMA -> cal.sofZmanShmaGRA
                ZmanEvent.SOF_ZMAN_TEFILLAH -> cal.sofZmanTfilaGRA
                ZmanEvent.CHATZOT -> cal.chatzos
                ZmanEvent.MINCHA_GEDOLAH -> cal.minchaGedola
                ZmanEvent.PLAG_HAMINCHA -> cal.plagHamincha
                ZmanEvent.SUNSET -> cal.sunset
                ZmanEvent.TZEIT_HAKOCHAVIM -> cal.tzaisGeonim5Point95Degrees
                ZmanEvent.TZEIT_RT -> cal.tzais72
            }
            ZmanimMinhag.CUSTOM -> when (event) {
                ZmanEvent.ALOT_HASHACHAR -> applyCustomBeforeSunrise(cal, settings.customAlot)
                ZmanEvent.MISHEYAKIR -> cal.misheyakir10Point2Degrees
                ZmanEvent.SUNRISE -> cal.sunrise
                ZmanEvent.SOF_ZMAN_SHMA -> cal.sofZmanShmaGRA
                ZmanEvent.SOF_ZMAN_TEFILLAH -> cal.sofZmanTfilaGRA
                ZmanEvent.CHATZOT -> cal.chatzos
                ZmanEvent.MINCHA_GEDOLAH -> cal.minchaGedola
                ZmanEvent.PLAG_HAMINCHA -> cal.plagHamincha
                ZmanEvent.SUNSET -> cal.sunset
                ZmanEvent.TZEIT_HAKOCHAVIM -> applyCustomAfterSunset(cal, settings.customTzeit)
                ZmanEvent.TZEIT_RT -> cal.tzais72
            }
        } ?: return null

        return Calendar.getInstance(tz).apply {
            time = baseDate
            add(Calendar.MINUTE, offsetMinutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun applyCustomBeforeSunrise(cal: ComplexZmanimCalendar, rule: CustomZmanRule): Date? {
        val sr = cal.sunrise ?: return null
        return when (rule.basis) {
            CalculationBasis.DEGREES -> cal.getSunriseOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + rule.value)
            CalculationBasis.FIXED_MINUTES -> Date(sr.time - (rule.value * 60_000L).toLong())
            CalculationBasis.ZMANIYOT_MINUTES -> Date(sr.time - ((cal.shaahZmanisGra / 60.0) * rule.value).toLong())
        }
    }

    private fun applyCustomAfterSunset(cal: ComplexZmanimCalendar, rule: CustomZmanRule): Date? {
        val ss = cal.sunset ?: return null
        return when (rule.basis) {
            CalculationBasis.DEGREES -> cal.getSunsetOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + rule.value)
            CalculationBasis.FIXED_MINUTES -> Date(ss.time + (rule.value * 60_000L).toLong())
            CalculationBasis.ZMANIYOT_MINUTES -> Date(ss.time + ((cal.shaahZmanisGra / 60.0) * rule.value).toLong())
        }
    }

    fun getHebrewDateLetters(): String {
        val jc = JewishCalendar()
        val hdf = HebrewDateFormatter().apply { isHebrewFormat = true }
        val day = hdf.formatHebrewNumber(jc.jewishDayOfMonth)
        val month = hdf.formatMonth(jc)
        val yearStr = hdf.formatHebrewNumber(jc.jewishYear % 1000)
        return "$day $month ה׳$yearStr"
    }
}