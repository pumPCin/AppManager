// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Progress emitted by an {@link Action} during execution.
 */
public final class ActionProgress {
    public enum State {STARTED, UPDATED, COMPLETED}

    @NonNull
    public final State state;
    @NonNull
    public final ActionExecutionMode executionMode;
    @NonNull
    public final List<ActionResult> results;
    /**
     * Maximum value used when the current operation has no measurable progress.
     */
    public static final int INDETERMINATE = -1;

    @Nullable
    public final CharSequence label;
    public final int max;
    public final float current;

    public ActionProgress(@Nullable CharSequence label, int max, float current) {
        this(label, max, current, State.UPDATED, ActionExecutionMode.BATCH,
                Collections.emptyList());
    }

    public ActionProgress(@Nullable CharSequence label, int max, float current,
                          @NonNull State state, @NonNull ActionExecutionMode executionMode,
                          @NonNull List<ActionResult> results) {
        if (max < 0 && max != INDETERMINATE) {
            throw new IllegalArgumentException("Invalid progress maximum: " + max);
        }
        if (Float.isNaN(current) || Float.isInfinite(current)) {
            throw new IllegalArgumentException("Invalid progress value: " + current);
        }
        if (max == INDETERMINATE && current != 0) {
            throw new IllegalArgumentException("Indeterminate progress has no numeric position");
        }
        if (max != INDETERMINATE && (current < 0 || current > max)) {
            throw new IllegalArgumentException(
                    "Progress value must be within [0, max]: " + current + "/" + max);
        }
        this.label = label == null ? null : label.toString();
        this.max = max;
        this.current = current;
        this.state = state;
        this.executionMode = executionMode;
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    @VisibleForTesting
    @NonNull
    public static ActionProgress indeterminate(@Nullable CharSequence label) {
        return new ActionProgress(label, INDETERMINATE, 0);
    }

    @VisibleForTesting
    @NonNull
    public static ActionProgress determinate(@Nullable CharSequence label, int max, float current) {
        if (max < 0) {
            throw new IllegalArgumentException("Determinate progress requires a non-negative max");
        }
        return new ActionProgress(label, max, current);
    }

    public boolean isIndeterminate() {
        return max == INDETERMINATE;
    }
}
