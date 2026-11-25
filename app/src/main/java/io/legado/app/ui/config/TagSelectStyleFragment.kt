package io.legado.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.setEdgeEffectColor

/**
 * 选择标签页样式设置，由标签配置页菜单进入
 */
class TagSelectStyleFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_tag_select_style)
        upSummaries()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.tag_select_style)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PreferKey.tagGroupTextSize -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_group_text_size))
                .setMaxValue(30)
                .setMinValue(8)
                .setValue(AppConfig.tagGroupTextSize)
                .show { AppConfig.tagGroupTextSize = it }

            PreferKey.tagTextSize -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_text_size))
                .setMaxValue(30)
                .setMinValue(8)
                .setValue(AppConfig.tagTextSize)
                .show { AppConfig.tagTextSize = it }

            PreferKey.tagBoxPaddingHorizontal -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_box_padding_horizontal))
                .setMaxValue(20)
                .setMinValue(0)
                .setValue(AppConfig.tagBoxPaddingHorizontal)
                .show { AppConfig.tagBoxPaddingHorizontal = it }

            PreferKey.tagBoxPaddingVertical -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_box_padding_vertical))
                .setMaxValue(20)
                .setMinValue(0)
                .setValue(AppConfig.tagBoxPaddingVertical)
                .show { AppConfig.tagBoxPaddingVertical = it }

            PreferKey.tagBoxCornerRadius -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_box_corner_radius))
                .setMaxValue(30)
                .setMinValue(0)
                .setValue(AppConfig.tagBoxCornerRadius)
                .show { AppConfig.tagBoxCornerRadius = it }

            PreferKey.tagBoxBorder -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_box_border))
                .setMaxValue(10)
                .setMinValue(0)
                .setValue(AppConfig.tagBoxBorder)
                .show { AppConfig.tagBoxBorder = it }

            PreferKey.tagBoxMargin -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_box_margin))
                .setMaxValue(20)
                .setMinValue(0)
                .setValue(AppConfig.tagBoxMargin)
                .show { AppConfig.tagBoxMargin = it }

            PreferKey.tagDividerMarginTop -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_divider_margin_top))
                .setMaxValue(30)
                .setMinValue(0)
                .setValue(AppConfig.tagDividerMarginTop)
                .show { AppConfig.tagDividerMarginTop = it }

            PreferKey.tagDividerMarginBottom -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.tag_divider_margin_bottom))
                .setMaxValue(30)
                .setMinValue(0)
                .setValue(AppConfig.tagDividerMarginBottom)
                .show { AppConfig.tagDividerMarginBottom = it }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.tagGroupTextSize,
            PreferKey.tagTextSize,
            PreferKey.tagBoxPaddingHorizontal,
            PreferKey.tagBoxPaddingVertical,
            PreferKey.tagBoxCornerRadius,
            PreferKey.tagBoxBorder,
            PreferKey.tagBoxMargin,
            PreferKey.tagDividerMarginTop,
            PreferKey.tagDividerMarginBottom -> upSummaries()
        }
    }

    private fun upSummaries() {
        upPreferenceSummary(PreferKey.tagGroupTextSize, "${AppConfig.tagGroupTextSize} sp")
        upPreferenceSummary(PreferKey.tagTextSize, "${AppConfig.tagTextSize} sp")
        upPreferenceSummary(PreferKey.tagBoxPaddingHorizontal, "${AppConfig.tagBoxPaddingHorizontal} dp")
        upPreferenceSummary(PreferKey.tagBoxPaddingVertical, "${AppConfig.tagBoxPaddingVertical} dp")
        upPreferenceSummary(PreferKey.tagBoxCornerRadius, "${AppConfig.tagBoxCornerRadius} dp")
        upPreferenceSummary(PreferKey.tagBoxBorder, "${AppConfig.tagBoxBorder} dp")
        upPreferenceSummary(PreferKey.tagBoxMargin, "${AppConfig.tagBoxMargin} dp")
        upPreferenceSummary(PreferKey.tagDividerMarginTop, "${AppConfig.tagDividerMarginTop} dp")
        upPreferenceSummary(PreferKey.tagDividerMarginBottom, "${AppConfig.tagDividerMarginBottom} dp")
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String) {
        findPreference<Preference>(preferenceKey)?.summary = value
    }
}
