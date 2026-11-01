// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.R;

/**
 * Common base for Actions whose state belongs to a package/user pair.
 */
public abstract class PackageScopedAction extends Action {
    protected PackageScopedAction(@NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                  @NonNull ActionParameters onParameters,
                                  @Nullable ActionParameters offParameters) {
        super(kind, target, onParameters, offParameters);
        assert getTarget().getScope() == ActionScope.PACKAGE;
    }

    protected PackageScopedAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                  @NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                  @NonNull ActionParameters onParameters,
                                  @Nullable ActionParameters offParameters,
                                  @NonNull Strictness strictness,
                                  @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, kind, target, onParameters, offParameters,
                strictness, revertMode);
        assert getTarget().getScope() == ActionScope.PACKAGE;
    }

    protected PackageScopedAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
        assert getTarget().getScope() == ActionScope.PACKAGE;
    }

    @NonNull
    protected final List<UserPackagePair> resolveTargets(@NonNull ActionContext context) {
        return context.resolveTargets(this);
    }

    @Override
    public final int getProgressMax(@NonNull ActionContext context) {
        return resolveTargets(context).size();
    }

    @NonNull
    protected CharSequence getProgressLabel(@NonNull ActionContext context,
                                            @NonNull UserPackagePair target) {
        Context ctx = ContextUtils.getContext();
        CharSequence appLabel = PackageUtils.getPackageLabel(ctx.getPackageManager(),
                target.getPackageName(), target.getUserId());
        return context.executionMode == ActionExecutionMode.SINGLETON
                ? ctx.getString(R.string.action_running_on_package, appLabel)
                : ctx.getString(R.string.action_running_for_package, toLocalizedString(ctx), appLabel);
    }

    @Nullable
    private CharSequence progressLabel(@NonNull ActionContext context, @NonNull UserPackagePair target) {
        try {
            return getProgressLabel(context, target);
        } catch (RuntimeException ignored) {
            return target.getPackageName();
        }
    }

    @NonNull
    protected final ActionResult actionResult(@NonNull UserPackagePair target,
                                              @NonNull ActionResultStatus status,
                                              @Nullable String message) {
        return super.actionResult(target, status, null, null, message);
    }

    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) throws Throwable {
    }

    @NonNull
    protected abstract ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                              @NonNull UserPackagePair target,
                                              @NonNull List<ActionExecutionRecord> previousRecords) throws Throwable;

    protected void onPostExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                 @NonNull List<UserPackagePair> targets,
                                 @NonNull List<ActionResult> results) throws Throwable {
    }

    @Nullable
    protected ActionState getCurrentState(@NonNull UserPackagePair target) throws Throwable {
        return null;
    }

    @Nullable
    protected ActionState getOnState(@NonNull UserPackagePair target) throws Throwable {
        return null;
    }

    @Nullable
    protected ActionState getOffState(@NonNull UserPackagePair target) throws Throwable {
        return null;
    }

    @NonNull
    @Override
    public final List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                            @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        int progressMax = targets.size();
        int targetIndex = 0;
        reportProgress(context, null, progressMax, 0, ActionProgress.State.STARTED, results);
        try {
            onPreExecute(context, mode, targets, previousRecords);
            for (UserPackagePair target : targets) {
                CharSequence label = progressLabel(context, target);
                reportProgress(context, label, progressMax, targetIndex);
                ActionResult result;
                ActionState currentState = null;
                try {
                    ActionState onState = getOnState(target);
                    ActionState offState = getOffState(target);
                    if (mode == ActionMode.OFF && getOffParameters() == null) {
                        result = actionResult(target, ActionResultStatus.UNSUPPORTED,
                                "Action has no reverse operation");
                    } else if (onState == null && offState == null) {
                        // Stateless actions, such as clear-data, have no state to compare.
                        result = onExecute(context, mode, target, previousRecords);
                    } else {
                        currentState = getCurrentState(target);
                        if (currentState == null || onState == null || offState == null) {
                            throw new IllegalStateException(
                                    "Package action must provide all states or none");
                        }
                        ActionState requestedState = mode == ActionMode.ON ? onState : offState;
                        if (currentState.isSame(requestedState)) {
                            result = actionResult(target, ActionResultStatus.UNCHANGED,
                                    currentState, currentState, null);
                        } else if (mode == ActionMode.OFF && getStrictness() == Strictness.STRICT
                                && !currentState.isSame(onState)) {
                            result = actionResult(target, ActionResultStatus.SKIPPED_STRICT_MISMATCH,
                                    ActionConflict.EXTERNAL_CHANGE, currentState, null,
                                    "Current state does not match the expected ON state");
                        } else {
                            result = onExecute(context, mode, target, previousRecords);
                            ActionState writtenState = result.writtenState;
                            if (writtenState == null && result.status == ActionResultStatus.SUCCESS) {
                                try {
                                    writtenState = getCurrentState(target);
                                } catch (Throwable ignored) {
                                    // The mutation succeeded, but its resulting state was unreadable.
                                }
                            }
                            result = result.withStateSnapshots(currentState, writtenState);
                        }
                    }
                } catch (Throwable throwable) {
                    result = actionResult(target, ActionResultStatus.FAILED,
                            currentState, null, throwable.getMessage());
                }
                results.add(result);
                targetIndex++;
                reportProgress(context, label, progressMax, targetIndex);
            }
        } catch (Throwable throwable) {
            for (; targetIndex < targets.size(); targetIndex++) {
                UserPackagePair target = targets.get(targetIndex);
                results.add(actionResult(target, ActionResultStatus.FAILED,
                        throwable.getMessage()));
                reportProgress(context, progressLabel(context, target), progressMax,
                        targetIndex + 1);
            }
        } finally {
            try {
                onPostExecute(context, mode, targets, results);
            } catch (Throwable ignored) {
                // Cleanup failures must not discard per-target execution results.
            } finally {
                reportProgress(context, null, progressMax, progressMax,
                        ActionProgress.State.COMPLETED, results);
            }
        }
        return results;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return super.toLocalizedString(context);
    }
}
