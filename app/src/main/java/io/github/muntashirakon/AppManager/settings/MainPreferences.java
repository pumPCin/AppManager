// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
//import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
//import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.preference.InfoAlertPreference;
import io.github.muntashirakon.preference.WarningAlertPreference;

public class MainPreferences extends PreferenceFragment {
    @NonNull
    public static MainPreferences getInstance(@Nullable String key) {
        MainPreferences preferences = new MainPreferences();
        Bundle args = new Bundle();
        args.putString(PREF_KEY, key);
        preferences.setArguments(args);
        return preferences;
    }

    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private FragmentActivity mActivity;
    private String mCurrentLang;
    private MainPreferencesViewModel mModel;
    private Preference mModePref;
    private String[] mModes;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = requireActivity();
        // Expiry notice
        //WarningAlertPreference buildExpiringNotice = requirePreference("app_manager_expiring_notice");
        //buildExpiringNotice.setVisible(!Boolean.FALSE.equals(BuildExpiryChecker.buildExpired()));
        // Funding campaign notice
        //InfoAlertPreference fundingCampaignNotice = requirePreference("funding_campaign_notice");
        //fundingCampaignNotice.setVisible(FundingCampaignChecker.campaignRunning());
        // Custom locale
        mCurrentLang = Prefs.Appearance.getLanguage();
        Map<String, Locale> locales = LangUtils.getAppLanguages(mActivity);
        final CharSequence[] languageNames = getLanguagesL(locales);
        final String[] languages = new String[languageNames.length];
        int i = 0;
        int localeIndex = 0;
        for (Map.Entry<String, Locale> localeEntry : locales.entrySet()) {
            languages[i] = localeEntry.getKey();
            if (languages[i].equals(mCurrentLang)) {
                localeIndex = i;
            }
            ++i;
        }
        Preference locale = requirePreference("custom_locale");
        locale.setSummary(languageNames[localeIndex]);
        int finalLocaleIndex = localeIndex;
        locale.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(mActivity, languages, languageNames)
                    .setTitle(R.string.choose_language)
                    .setSelectionIndex(finalLocaleIndex)
                    .setPositiveButton(R.string.apply, (dialog, which, selectedItem) -> {
                        if (selectedItem != null) {
                            mCurrentLang = selectedItem;
                            Prefs.Appearance.setLanguage(mCurrentLang);
                            AppearanceUtils.applyConfigurationChangesToActivities();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Mode of operation
        mModePref = requirePreference("mode_of_operations");
        mModes = getResources().getStringArray(R.array.modes);

        mModel.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            UIUtils.displayShortToast(R.string.the_operation_was_successful);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mModePref != null) {
            mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                    mModes[MODE_NAMES.indexOf(Ops.getMode())], Ops.getInferredMode(mActivity)));
        }
    }

    @Override
    public int getTitle() {
        return R.string.settings;
    }

    @NonNull
    private CharSequence[] getLanguagesL(@NonNull Map<String, Locale> locales) {
        CharSequence[] localesL = new CharSequence[locales.size()];
        Locale locale;
        int i = 0;
        for (Map.Entry<String, Locale> localeEntry : locales.entrySet()) {
            locale = localeEntry.getValue();
            if (LangUtils.LANG_AUTO.equals(localeEntry.getKey())) {
                localesL[i] = mActivity.getString(R.string.auto);
            } else localesL[i] = locale.getDisplayName(locale);
            ++i;
        }
        return localesL;
    }
}
