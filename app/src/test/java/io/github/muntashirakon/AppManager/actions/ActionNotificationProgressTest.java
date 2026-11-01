// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;

import io.github.muntashirakon.AppManager.progress.ActionProgressText;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationManagerInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ActionNotificationProgressTest {
    @Test
    public void singletonSeparatesTitleTaskAndProgressText() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Running on Maps…", "Profiles · 37% complete", 8, 3, 10000, 3750);
    }

    @Test
    public void batchKeepsPlanTitleAndSeparatesTaskAndProgressText() {
        verify(ActionExecutionMode.BATCH, "My profile", "Running on Maps…", "Profiles · 37% complete", 8, 3, 10000, 3750);
    }

    @Test
    public void indeterminateSingletonHasNoPercentageOrCompletionAlert() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Running on Maps…", "Profiles", -1, 0, 0, 0);
    }

    @Test
    public void singletonFractionalUnitShowsHalfFilledBar() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Running on Maps…", "Profiles · 50% complete", 1, 0.5f, 10000, 5000);
    }

    @Test
    public void batchFractionalUnitShowsHalfFilledBar() {
        verify(ActionExecutionMode.BATCH, "My profile", "Running on Maps…", "Profiles · 50% complete", 1, 0.5f, 10000, 5000);
    }

    @Test
    public void singletonRespectsCustomFormatter() {
        verify(ActionExecutionMode.SINGLETON, "Freeze", "Custom progress", "Profiles", 8, 3, 10000, 3750,
                handler -> "Custom progress");
    }

    @Test
    public void batchRespectsCountFormatter() {
        verify(ActionExecutionMode.BATCH, "Freeze", "3/8", "Profiles", 8, 3, 10000, 3750,
                ProgressHandler.PROGRESS_REGULAR);
    }

    @Test
    public void batchRespectsCustomLabelAndText() {
        verify(ActionExecutionMode.BATCH, "Custom title", "Custom task", "Profiles · Custom text", 8, 3, 10000, 3750,
                new ProgressHandler.ProgressTextInterface() {
                    @Override
                    public CharSequence getTitle(@NonNull ProgressHandler handler) {
                        return "Custom title";
                    }

                    @Override
                    public CharSequence getCurrentTaskLabel(@NonNull ProgressHandler handler) {
                        return "Custom task";
                    }

                    @Override
                    public CharSequence getProgressText(@NonNull ProgressHandler handler) {
                        return "Custom text";
                    }
                });
    }

    private void verify(ActionExecutionMode mode, String title, String text, String subtext, int max, float current,
                        int barMax, int barCurrent) {
        verify(mode, title, text, subtext, max, current, barMax, barCurrent,
                new ActionProgressText(RuntimeEnvironment.getApplication(), title));
    }

    private void verify(ActionExecutionMode mode, String title, String text, String subtext, int max, float current,
                        int barMax, int barCurrent, ProgressHandler.ProgressTextInterface formatter) {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        NotificationManagerInfo channel = new NotificationManagerInfo("progress", "Progress", 2);
        NotificationProgressHandler handler = new NotificationProgressHandler(app, channel, channel, null);
        handler.setProgressTextInterface(formatter);
        NotificationInfo parent = new NotificationInfo().setTitle("Freeze")
                .setBody("Operation running…").setOperationName("Profiles");
        handler.onAttach(null, parent);
        handler.postActionProgress(new ActionProgress(null, max, 0, ActionProgress.State.STARTED,
                mode, Collections.emptyList()));
        handler.postActionProgress(new ActionProgress("Running on Maps…", max, current,
                ActionProgress.State.UPDATED, mode, Collections.emptyList()));
        Notification notification = Shadows.shadowOf(manager).getAllNotifications().get(0);
        assertEquals(title, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(text, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(subtext, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString());
        assertEquals(max == -1, notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE));
        assertEquals(barMax, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX));
        assertEquals(barCurrent, notification.extras.getInt(Notification.EXTRA_PROGRESS));
        assertEquals(max, handler.getLastMax());
        assertEquals(current, handler.getLastProgress(), 0f);
        assertSame(parent, handler.getLastMessage());
        handler.postActionProgress(new ActionProgress(null, max, current,
                ActionProgress.State.COMPLETED, mode, Collections.emptyList()));
        assertTrue(Shadows.shadowOf(manager).getAllNotifications().isEmpty());
    }
}
