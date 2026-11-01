// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.actions;

/**
 * Describes whether an execution context represents one Action or a complete ActionPlan.
 */
public enum ActionExecutionMode {
    SINGLETON,
    BATCH
}
