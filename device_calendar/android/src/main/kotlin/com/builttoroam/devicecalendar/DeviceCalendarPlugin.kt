package com.builttoroam.devicecalendar

import android.app.Activity
import android.content.Context
import com.builttoroam.devicecalendar.common.DayOfWeek
import com.builttoroam.devicecalendar.common.RecurrenceFrequency
import com.builttoroam.devicecalendar.models.Attendee
import com.builttoroam.devicecalendar.models.Event
import com.builttoroam.devicecalendar.models.RecurrenceRule
import com.builttoroam.devicecalendar.models.Reminder
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

const val CHANNEL_NAME = "plugins.builttoroam.com/device_calendar"

class DeviceCalendarPlugin() : MethodCallHandler {
    // Methods
    private val REQUEST_PERMISSIONS_METHOD = "requestPermissions"
    private val HAS_PERMISSIONS_METHOD = "hasPermissions"
    private val RETRIEVE_CALENDARS_METHOD = "retrieveCalendars"
    private val RETRIEVE_EVENTS_METHOD = "retrieveEvents"
    private val DELETE_EVENT_METHOD = "deleteEvent"
    private val CREATE_OR_UPDATE_EVENT_METHOD = "createOrUpdateEvent"

    // Method arguments
    private val CALENDAR_ID_ARGUMENT = "calendarId"
    private val START_DATE_ARGUMENT = "startDate"
    private val END_DATE_ARGUMENT = "endDate"
    private val EVENT_IDS_ARGUMENT = "eventIds"
    private val EVENT_ID_ARGUMENT = "eventId"
    private val EVENT_TITLE_ARGUMENT = "eventTitle"
    private val EVENT_LOCATION_ARGUMENT = "eventLocation"
    private val EVENT_URL_ARGUMENT = "eventURL"
    private val EVENT_DESCRIPTION_ARGUMENT = "eventDescription"
    private val EVENT_ALL_DAY_ARGUMENT = "eventAllDay"
    private val EVENT_START_DATE_ARGUMENT = "eventStartDate"
    private val EVENT_END_DATE_ARGUMENT = "eventEndDate"
    private val RECURRENCE_RULE_ARGUMENT = "recurrenceRule"
    private val RECURRENCE_FREQUENCY_ARGUMENT = "recurrenceFrequency"
    private val TOTAL_OCCURRENCES_ARGUMENT = "totalOccurrences"
    private val INTERVAL_ARGUMENT = "interval"
    private val DAYS_OF_WEEK_ARGUMENT = "daysOfWeek"
    private val DAY_OF_MONTH_ARGUMENT = "dayOfMonth"
    private val MONTH_OF_YEAR_ARGUMENT = "monthOfYear"
    private val WEEK_OF_MONTH_ARGUMENT = "weekOfMonth"
    private val ATTENDEES_ARGUMENT = "attendees"
    private val EMAIL_ADDRESS_ARGUMENT = "emailAddress"
    private val NAME_ARGUMENT = "name"
    private val ROLE_ARGUMENT = "role"
    private val REMINDERS_ARGUMENT = "reminders"
    private val MINUTES_ARGUMENT = "minutes"

    private lateinit var _registrar: Registrar
    private lateinit var _calendarDelegate: CalendarDelegate

    private constructor(registrar: Registrar, calendarDelegate: CalendarDelegate) : this() {
        _registrar = registrar
        _calendarDelegate = calendarDelegate
    }

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val context: Context = registrar.context()
            val activity: Activity? = registrar.activity()

            val calendarDelegate = CalendarDelegate(activity, context)
            val instance = DeviceCalendarPlugin(registrar, calendarDelegate)

            val calendarsChannel = MethodChannel(registrar.messenger(), CHANNEL_NAME)
            calendarsChannel.setMethodCallHandler(instance)

            registrar.addRequestPermissionsResultListener(calendarDelegate)
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            REQUEST_PERMISSIONS_METHOD -> {
                _calendarDelegate.requestPermissions(result)
            }
            HAS_PERMISSIONS_METHOD -> {
                _calendarDelegate.hasPermissions(result)
            }
            RETRIEVE_CALENDARS_METHOD -> {
                _calendarDelegate.retrieveCalendars(result)
            }
            RETRIEVE_EVENTS_METHOD -> {
                val calendarId = call.argument<String>(CALENDAR_ID_ARGUMENT)
                val startDate = call.argument<Long>(START_DATE_ARGUMENT)
                val endDate = call.argument<Long>(END_DATE_ARGUMENT)
                val eventIds = call.argument<List<String>>(EVENT_IDS_ARGUMENT) ?: listOf()

                _calendarDelegate.retrieveEvents(calendarId!!, startDate, endDate, eventIds, result)
            }
            CREATE_OR_UPDATE_EVENT_METHOD -> {
                val calendarId = call.argument<String>(CALENDAR_ID_ARGUMENT)
                val event = parseEventArgs(call, calendarId)

                _calendarDelegate.createOrUpdateEvent(calendarId!!, event, result)
            }
            DELETE_EVENT_METHOD -> {
                val calendarId = call.argument<String>(CALENDAR_ID_ARGUMENT)
                val eventId = call.argument<String>(EVENT_ID_ARGUMENT)

                _calendarDelegate.deleteEvent(calendarId!!, eventId!!, result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun parseEventArgs(call: MethodCall, calendarId: String?): Event {
        val event = Event()
        event.title = call.argument<String>(EVENT_TITLE_ARGUMENT)
        event.calendarId = calendarId
        event.eventId = call.argument<String>(EVENT_ID_ARGUMENT)
        event.description = call.argument<String>(EVENT_DESCRIPTION_ARGUMENT)
        event.allDay = call.argument<Boolean>(EVENT_ALL_DAY_ARGUMENT) ?: false
        event.start = call.argument<Long>(EVENT_START_DATE_ARGUMENT)!!
        event.end = call.argument<Long>(EVENT_END_DATE_ARGUMENT)!!
        event.location = call.argument<String>(EVENT_LOCATION_ARGUMENT)
        event.url = call.argument<String>(EVENT_URL_ARGUMENT)

        if (call.hasArgument(RECURRENCE_RULE_ARGUMENT) && call.argument<Map<String, Any>>(RECURRENCE_RULE_ARGUMENT) != null) {
            val recurrenceRule = parseRecurrenceRuleArgs(call)
            event.recurrenceRule = recurrenceRule
        }

        if (call.hasArgument(ATTENDEES_ARGUMENT) && call.argument<List<Map<String, Any>>>(ATTENDEES_ARGUMENT) != null) {
            event.attendees = mutableListOf()
            val attendeesArgs = call.argument<List<Map<String, Any>>>(ATTENDEES_ARGUMENT)!!
            for (attendeeArgs in attendeesArgs) {
                event.attendees.add(Attendee(
                        attendeeArgs[EMAIL_ADDRESS_ARGUMENT] as String,
                        attendeeArgs[NAME_ARGUMENT] as String?,
                        attendeeArgs[ROLE_ARGUMENT] as Int,
                        null, null))
            }
        }

        if (call.hasArgument(REMINDERS_ARGUMENT) && call.argument<List<Map<String, Any>>>(REMINDERS_ARGUMENT) != null) {
            event.reminders = mutableListOf()
            val remindersArgs = call.argument<List<Map<String, Any>>>(REMINDERS_ARGUMENT)!!
            for (reminderArgs in remindersArgs) {
                event.reminders.add(Reminder(reminderArgs[MINUTES_ARGUMENT] as Int))
            }
        }

        return event
    }

    private fun parseRecurrenceRuleArgs(call: MethodCall): RecurrenceRule {
        val recurrenceRuleArgs = call.argument<Map<String, Any>>(RECURRENCE_RULE_ARGUMENT)!!
        val recurrenceFrequencyIndex = recurrenceRuleArgs[RECURRENCE_FREQUENCY_ARGUMENT] as Int
        val recurrenceRule = RecurrenceRule(RecurrenceFrequency.values()[recurrenceFrequencyIndex])
        if (recurrenceRuleArgs.containsKey(TOTAL_OCCURRENCES_ARGUMENT)) {
            recurrenceRule.totalOccurrences = recurrenceRuleArgs[TOTAL_OCCURRENCES_ARGUMENT] as Int
        }

        if (recurrenceRuleArgs.containsKey(INTERVAL_ARGUMENT)) {
            recurrenceRule.interval = recurrenceRuleArgs[INTERVAL_ARGUMENT] as Int
        }

        if (recurrenceRuleArgs.containsKey(END_DATE_ARGUMENT)) {
            recurrenceRule.endDate = recurrenceRuleArgs[END_DATE_ARGUMENT] as Long
        }

        if (recurrenceRuleArgs.containsKey(DAYS_OF_WEEK_ARGUMENT)) {
            recurrenceRule.daysOfWeek = recurrenceRuleArgs[DAYS_OF_WEEK_ARGUMENT].toListOf<Int>()?.map { DayOfWeek.values()[it] }?.toMutableList()
        }

        if (recurrenceRuleArgs.containsKey(DAY_OF_MONTH_ARGUMENT)) {
            recurrenceRule.dayOfMonth = recurrenceRuleArgs[DAY_OF_MONTH_ARGUMENT] as Int
        }

        if (recurrenceRuleArgs.containsKey(MONTH_OF_YEAR_ARGUMENT)) {
            recurrenceRule.monthOfYear = recurrenceRuleArgs[MONTH_OF_YEAR_ARGUMENT] as Int
        }

        if (recurrenceRuleArgs.containsKey(WEEK_OF_MONTH_ARGUMENT)) {
            recurrenceRule.weekOfMonth = recurrenceRuleArgs[WEEK_OF_MONTH_ARGUMENT] as Int
        }

        return recurrenceRule
    }

    private inline fun <reified T : Any> Any?.toListOf(): List<T>? {
        return (this as List<*>?)?.filterIsInstance<T>()?.toList()
    }

    private inline fun <reified T : Any> Any?.toMutableListOf(): MutableList<T>? {
        return this?.toListOf<T>()?.toMutableList()
    }
}
