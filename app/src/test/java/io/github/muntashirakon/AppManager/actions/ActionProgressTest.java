// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.actions.ActionContext.ProgressListener;

public class ActionProgressTest {
    @Test
    public void acceptsDeterminateProgress() {
        ActionProgress progress = ActionProgress.determinate("Running", 8, 3);

        assertEquals("Running", progress.label);
        assertEquals(8, progress.max);
        assertEquals(3f, progress.current, 0f);
        assertFalse(progress.isIndeterminate());
    }

    @Test
    public void acceptsIndeterminateProgress() {
        ActionProgress progress = ActionProgress.indeterminate("Running reboot...");

        assertEquals(ActionProgress.INDETERMINATE, progress.max);
        assertEquals(0f, progress.current, 0f);
        assertTrue(progress.isIndeterminate());
    }

    @Test
    public void rejectsInvalidDeterminateProgress() {
        try {
            new ActionProgress(null, 8, 9);
            fail("progress above maximum must be rejected");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new ActionProgress(null, 8, -1);
            fail("negative progress must be rejected");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new ActionProgress(null, -2, 0);
            fail("invalid negative maximum must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void listenersSendLatestProgressToLateSubscribers() {
        ActionContext listeners = progressContext();
        ActionProgress first = ActionProgress.determinate("First", 2, 1);
        ActionProgress second = ActionProgress.determinate("Second", 2, 2);
        List<ActionProgress> firstListener = new ArrayList<>();
        List<ActionProgress> secondListener = new ArrayList<>();

        ProgressListener firstConsumer = firstListener::add;
        ProgressListener secondConsumer = secondListener::add;
        listeners.addProgressListener(firstConsumer);
        listeners.dispatch(first);
        listeners.dispatch(second);
        listeners.addProgressListener(secondConsumer);

        assertEquals(2, firstListener.size());
        assertSame(first, firstListener.get(0));
        assertSame(second, firstListener.get(1));
        assertEquals(1, secondListener.size());
        assertSame(second, secondListener.get(0));
        assertSame(second, listeners.getLatestProgress());
    }

    @Test
    public void removedListenersStopReceivingProgress() {
        ActionContext listeners = progressContext();
        List<ActionProgress> received = new ArrayList<>();
        ProgressListener listener = received::add;
        listeners.addProgressListener(listener);
        listeners.removeProgressListener(listener);
        listeners.dispatch(ActionProgress.determinate(null, 1, 1));

        assertTrue(received.isEmpty());
    }

    @Test
    public void removalCancelsCallbacksAlreadyQueuedBehindAnotherListener() throws Exception {
        ActionContext source = progressContext();
        CountDownLatch deliveryEntered = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        source.addProgressListener(event -> {
            deliveryEntered.countDown();
            try {
                if (!releaseDelivery.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
            } catch (InterruptedException e) { throw new AssertionError(e); }
        });
        List<ActionProgress> received = Collections.synchronizedList(new ArrayList<>());
        ProgressListener removed = received::add;
        source.addProgressListener(removed);
        Thread publisher = new Thread(() -> source.dispatch(ActionProgress.determinate("first", 2, 1)));
        publisher.start();
        try {
            assertTrue(deliveryEntered.await(5, TimeUnit.SECONDS));
            source.dispatch(ActionProgress.determinate("queued", 2, 2));
            source.removeProgressListener(removed);
        } finally {
            releaseDelivery.countDown();
            publisher.join(5000);
        }
        assertFalse(publisher.isAlive());
        assertTrue(received.isEmpty());
    }

    @Test
    public void removalCancelsQueuedSnapshotReplay() {
        ActionContext source = progressContext();
        source.dispatch(ActionProgress.determinate("snapshot", 1, 0));
        List<ActionProgress> received = new ArrayList<>();
        ProgressListener removed = received::add;
        source.addProgressListener(event -> {
            source.addProgressListener(removed);
            source.removeProgressListener(removed);
        });
        assertTrue(received.isEmpty());
    }

    @Test
    public void registeringAgainDoesNotRevivePreviousQueuedDeliveries() {
        ActionContext source = progressContext();
        ActionProgress first = ActionProgress.determinate("first", 2, 1);
        ActionProgress second = ActionProgress.determinate("second", 2, 2);
        List<ActionProgress> received = new ArrayList<>();
        ProgressListener listener = received::add;
        source.addProgressListener(event -> {
            if (event == first) {
                source.dispatch(second);
                source.removeProgressListener(listener);
                source.addProgressListener(listener);
            }
        });
        source.addProgressListener(listener);
        source.dispatch(first);
        assertEquals(Collections.singletonList(second), received);
    }

    @Test
    public void concurrentSubscriptionCannotReplayAnOlderSnapshotAfterAnUpdate() throws Exception {
        ActionContext source = progressContext();
        ActionProgress first = ActionProgress.determinate("first", 2, 1);
        ActionProgress second = ActionProgress.determinate("second", 2, 2);
        source.dispatch(first);
        CountDownLatch replayEntered = new CountDownLatch(1);
        CountDownLatch releaseReplay = new CountDownLatch(1);
        List<ActionProgress> received = Collections.synchronizedList(new ArrayList<>());
        Thread subscriber = new Thread(() -> source.addProgressListener(event -> {
            if (event == first) {
                replayEntered.countDown();
                try {
                    if (!releaseReplay.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                } catch (InterruptedException e) { throw new AssertionError(e); }
            }
            received.add(event);
        }));
        subscriber.start();
        try {
            assertTrue(replayEntered.await(5, TimeUnit.SECONDS));
            source.dispatch(second);
        } finally {
            releaseReplay.countDown();
            subscriber.join(5000);
        }
        assertFalse(subscriber.isAlive());
        assertEquals(Arrays.asList(first, second), received);
    }

    @Test
    public void observerFailuresAndReentrantUpdatesDoNotDisruptOtherConsumers() {
        ActionContext source = progressContext();
        ActionProgress first = ActionProgress.determinate("first", 2, 1);
        ActionProgress second = ActionProgress.determinate("second", 2, 2);
        List<ActionProgress> received = new ArrayList<>();
        source.addProgressListener(event -> { throw new IllegalStateException("disconnected"); });
        source.addProgressListener(event -> { if (event == first) source.dispatch(second); });
        source.addProgressListener(received::add);
        source.dispatch(first);
        assertEquals(Arrays.asList(first, second), received);
    }

    @Test
    public void actionReportsProgressThroughRuntimeContext() {
        List<ActionProgress> received = new ArrayList<>();
        ReportingAction action = new ReportingAction();
        ActionContext context = new ActionContext(null, Collections.emptyList(), "plan",
                ActionExecutionMode.SINGLETON, 1L, false, null, received::add);

        action.execute(context, ActionMode.ON, Collections.emptyList());

        assertEquals(2, received.size());
        assertEquals("Running", received.get(0).label);
        assertEquals(1, received.get(0).max);
        assertEquals(0f, received.get(0).current, 0f);
        assertEquals("Working", received.get(1).label);
        assertTrue(received.get(1).isIndeterminate());
    }

    @Test
    public void actionCanExecuteWithoutProgressConsumer() {
        new ReportingAction().execute(new ActionContext(null, Collections.emptyList(), "plan",
                        ActionExecutionMode.SINGLETON, 1L, false),
                ActionMode.ON, Collections.emptyList());
    }

    @Test
    public void packageScopedLifecycleReportsEachTargetBeforeAndAfterExecution() {
        List<ActionProgress> received = new ArrayList<>();
        List<UserPackagePair> targets = Arrays.asList(
                new UserPackagePair("first", 0), new UserPackagePair("second", 10));
        PackageReportingAction action = new PackageReportingAction(targets);
        ActionContext context = new ActionContext(null, Collections.emptyList(), "plan",
                ActionExecutionMode.BATCH, 1L, false, null, received::add);

        action.execute(context, ActionMode.ON, Collections.emptyList());

        assertEquals(6, received.size());
        assertEquals(ActionProgress.State.STARTED, received.get(0).state);
        assertEquals("first", received.get(1).label);
        assertEquals(0f, received.get(1).current, 0f);
        assertEquals(1f, received.get(2).current, 0f);
        assertEquals("second", received.get(3).label);
        assertEquals(2f, received.get(4).current, 0f);
        assertEquals(2, received.get(4).max);
        assertEquals(ActionProgress.State.COMPLETED, received.get(5).state);
        assertEquals(2, received.get(5).results.size());
    }

    @Test
    public void globalScopedLifecycleReportsOneWorkUnit() {
        List<ActionProgress> received = new ArrayList<>();
        GlobalReportingAction action = new GlobalReportingAction();
        ActionContext context = new ActionContext(null, Collections.emptyList(), "plan",
                ActionExecutionMode.SINGLETON, 1L, false, null, received::add);

        action.execute(context, ActionMode.ON, Collections.emptyList());

        assertEquals(2, received.size());
        assertEquals("global", received.get(0).label);
        assertEquals(0f, received.get(0).current, 0f);
        assertEquals(-1, received.get(0).max);
        assertEquals(0f, received.get(1).current, 0f);
        assertEquals(ActionProgress.State.COMPLETED, received.get(1).state);
    }

    private static ActionContext progressContext() {
        return new ActionContext(null, Collections.emptyList(), "plan",
                ActionExecutionMode.BATCH, 1L, false);
    }

    private static final class PackageReportingAction extends PackageScopedAction {
        PackageReportingAction(List<UserPackagePair> targets) {
            super(ActionKind.FREEZE,
                    new ActionTargetSpec(new PackageTargetSpec(targets, false, false)),
                    new ActionParameters(), null);
        }

        @NonNull
        @Override
        protected CharSequence getProgressLabel(@NonNull ActionContext context,
                                                @NonNull UserPackagePair target) {
            return target.getPackageName();
        }

        @NonNull
        @Override
        protected ActionResult onExecute(@NonNull ActionContext context,
                                         @NonNull ActionMode mode,
                                         @NonNull UserPackagePair target,
                                         @NonNull List<ActionExecutionRecord> previousRecords) {
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        }
    }

    private static final class GlobalReportingAction extends GlobalScopedAction {
        GlobalReportingAction() {
            super(ActionKind.REBOOT,
                    new ActionTargetSpec(new GlobalTargetSpec(new JSONObject())),
                    new ActionParameters(), null);
        }

        @NonNull
        @Override
        protected CharSequence getProgressLabel(@NonNull ActionContext context) {
            return "global";
        }

        @NonNull
        @Override
        protected ActionResult onExecute(@NonNull ActionContext context,
                                         @NonNull ActionMode mode,
                                         @NonNull List<ActionExecutionRecord> previousRecords) {
            return globalResult(ActionResultStatus.SUCCESS, null);
        }
    }

    private static final class ReportingAction extends Action {
        ReportingAction() {
            super(ActionKind.FREEZE,
                    new ActionTargetSpec(new PackageTargetSpec(Collections.emptyList(), false, false)),
                    new ActionParameters(), new ActionParameters());
        }

        @NonNull
        @Override
        public List<ActionResult> execute(@NonNull ActionContext context,
                                          @NonNull ActionMode mode,
                                          @NonNull List<ActionExecutionRecord> previousRecords) {
            reportProgress(context, "Running", 1, 0);
            reportIndeterminateProgress(context, "Working");
            return Collections.singletonList(actionResult(new UserPackagePair("pkg", 0),
                    ActionResultStatus.SUCCESS, null, null, null));
        }
    }
}
