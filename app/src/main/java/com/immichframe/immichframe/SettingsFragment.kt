package com.immichframe.immichframe

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_view, rootKey)

        val closeButton = findPreference<Preference>("closeSettings")
        closeButton?.setOnPreferenceClickListener {
            activity?.setResult(Activity.RESULT_OK)
            activity?.finish()
            true
        }

        val timePref = findPreference<EditTextPreference>("dim_time_range")
        timePref?.setOnPreferenceChangeListener { _, newValue ->
            val timeRange = newValue.toString().trim()

            val regex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])-([01]?[0-9]|2[0-3]):([0-5][0-9])$".toRegex()
            if (timeRange.matches(regex)) {
                val (start, end) = timeRange.split("-")
                val (startHour, startMinute) = start.split(":").map { it.toInt() }
                val (endHour, endMinute) = end.split(":").map { it.toInt() }

                // Save parsed time values separately if needed
                val context = requireContext()
                context.getSharedPreferences("ImmichFramePrefs", Context.MODE_PRIVATE).edit()
                    .putInt("dimStartHour", startHour)
                    .putInt("dimStartMinute", startMinute)
                    .putInt("dimEndHour", endHour)
                    .putInt("dimEndMinute", endMinute)
                    .apply()

                true // Accept new value
            } else {
                Toast.makeText(requireContext(), "Invalid time format. Use HH:mm-HH:mm.", Toast.LENGTH_LONG).show()
                false // Reject value change
            }
        }
    }
}