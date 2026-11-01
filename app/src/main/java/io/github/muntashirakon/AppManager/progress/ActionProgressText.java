// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.progress;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.actions.ActionProgress;

/**
 * Formats Action progress text.
 */
public final class ActionProgressText implements ProgressHandler.ProgressTextInterface {
    @NonNull
    private final Context mContext;
    @Nullable
    private final CharSequence mTitle;
    @Nullable
    private CharSequence mLabel;

    public ActionProgressText(@NonNull Context context, @Nullable CharSequence title) {
        mContext = context;
        mTitle = title == null ? null : title.toString();
    }

    @Nullable
    @Override
    public CharSequence getTitle(@NonNull ProgressHandler progressHandler) {
        return mTitle;
    }

    @Nullable
    @Override
    public CharSequence getCurrentTaskLabel(@NonNull ProgressHandler progressHandler) {
        ActionProgress progress = progressHandler.getLastActionProgress();
        if (progress == null || progress.state == ActionProgress.State.STARTED) {
            mLabel = null;
        }
        if (progress != null && progress.label != null) {
            mLabel = progress.label;
        }
        return mLabel;
    }

    @Nullable
    @Override
    public CharSequence getProgressText(@NonNull ProgressHandler progressHandler) {
        int max = progressHandler.getLastMax();
        if (max <= 0) {
            return null;
        }
        int percent = (int) (progressHandler.getLastProgress() / max * 100);
        return mContext.getString(R.string.progress_percent_complete, percent);
    }
}
