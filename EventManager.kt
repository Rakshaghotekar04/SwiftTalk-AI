package com.example.swifttalkai

import java.util.Calendar

object EventManager {

    private val events = mutableListOf<VisitEvent>()

    fun addEvent(event: VisitEvent) {
        events.add(event)
    }

    fun searchEvent(hour: Int): VisitEvent? {

        return events.find {

            val cal = Calendar.getInstance()
            cal.timeInMillis = it.time
            cal.get(Calendar.HOUR_OF_DAY) == hour
        }
    }
}