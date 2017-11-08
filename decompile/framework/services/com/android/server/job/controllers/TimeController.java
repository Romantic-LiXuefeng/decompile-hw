package com.android.server.job.controllers;

import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.content.Context;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.util.TimeUtils;
import com.android.server.job.JobSchedulerService;
import com.android.server.job.StateChangedListener;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class TimeController extends StateController {
    private static final String TAG = "JobScheduler.Time";
    private static TimeController mSingleton;
    private final String DEADLINE_TAG = "*job.deadline*";
    private final String DELAY_TAG = "*job.delay*";
    private AlarmManager mAlarmService = null;
    private final OnAlarmListener mDeadlineExpiredListener = new OnAlarmListener() {
        public void onAlarm() {
            TimeController.this.checkExpiredDeadlinesAndResetAlarm();
        }
    };
    private long mNextDelayExpiredElapsedMillis = JobStatus.NO_LATEST_RUNTIME;
    private final OnAlarmListener mNextDelayExpiredListener = new OnAlarmListener() {
        public void onAlarm() {
            TimeController.this.checkExpiredDelaysAndResetAlarm();
        }
    };
    private long mNextJobExpiredElapsedMillis = JobStatus.NO_LATEST_RUNTIME;
    private final List<JobStatus> mTrackedJobs = new LinkedList();

    public static synchronized TimeController get(JobSchedulerService jms) {
        TimeController timeController;
        synchronized (TimeController.class) {
            if (mSingleton == null) {
                mSingleton = new TimeController(jms, jms.getContext(), jms.getLock());
            }
            timeController = mSingleton;
        }
        return timeController;
    }

    private TimeController(StateChangedListener stateChangedListener, Context context, Object lock) {
        super(stateChangedListener, context, lock);
    }

    public void maybeStartTrackingJobLocked(JobStatus job, JobStatus lastJob) {
        long j = JobStatus.NO_LATEST_RUNTIME;
        if (job.hasTimingDelayConstraint() || job.hasDeadlineConstraint()) {
            maybeStopTrackingJobLocked(job, null, false);
            boolean isInsert = false;
            ListIterator<JobStatus> it = this.mTrackedJobs.listIterator(this.mTrackedJobs.size());
            while (it.hasPrevious()) {
                if (((JobStatus) it.previous()).getLatestRunTimeElapsed() < job.getLatestRunTimeElapsed()) {
                    isInsert = true;
                    break;
                }
            }
            if (isInsert) {
                it.next();
            }
            it.add(job);
            long earliestRunTime = job.hasTimingDelayConstraint() ? job.getEarliestRunTime() : JobStatus.NO_LATEST_RUNTIME;
            if (job.hasDeadlineConstraint()) {
                j = job.getLatestRunTimeElapsed();
            }
            maybeUpdateAlarmsLocked(earliestRunTime, j, job.getSourceUid());
        }
    }

    public void maybeStopTrackingJobLocked(JobStatus job, JobStatus incomingJob, boolean forUpdate) {
        if (this.mTrackedJobs.remove(job)) {
            checkExpiredDelaysAndResetAlarm();
            checkExpiredDeadlinesAndResetAlarm();
        }
    }

    private boolean canStopTrackingJobLocked(JobStatus job) {
        boolean z = true;
        if (job.hasTimingDelayConstraint() && (job.satisfiedConstraints & 2) == 0) {
            return false;
        }
        if (job.hasDeadlineConstraint() && (job.satisfiedConstraints & 4) == 0) {
            z = false;
        }
        return z;
    }

    private void ensureAlarmServiceLocked() {
        if (this.mAlarmService == null) {
            this.mAlarmService = (AlarmManager) this.mContext.getSystemService("alarm");
        }
    }

    private void checkExpiredDeadlinesAndResetAlarm() {
        synchronized (this.mLock) {
            long nextExpiryTime = JobStatus.NO_LATEST_RUNTIME;
            int nextExpiryUid = 0;
            long nowElapsedMillis = SystemClock.elapsedRealtime();
            Iterator<JobStatus> it = this.mTrackedJobs.iterator();
            while (it.hasNext()) {
                JobStatus job = (JobStatus) it.next();
                if (job.hasDeadlineConstraint()) {
                    long jobDeadline = job.getLatestRunTimeElapsed();
                    if (jobDeadline > nowElapsedMillis) {
                        nextExpiryTime = jobDeadline;
                        nextExpiryUid = job.getSourceUid();
                        break;
                    }
                    if (job.hasTimingDelayConstraint()) {
                        job.setTimingDelayConstraintSatisfied(true);
                    }
                    job.setDeadlineConstraintSatisfied(true);
                    this.mStateChangedListener.onRunJobNow(job);
                    it.remove();
                }
            }
            setDeadlineExpiredAlarmLocked(nextExpiryTime, nextExpiryUid);
        }
    }

    private void checkExpiredDelaysAndResetAlarm() {
        synchronized (this.mLock) {
            long nowElapsedMillis = SystemClock.elapsedRealtime();
            long nextDelayTime = JobStatus.NO_LATEST_RUNTIME;
            int nextDelayUid = 0;
            boolean ready = false;
            Iterator<JobStatus> it = this.mTrackedJobs.iterator();
            while (it.hasNext()) {
                JobStatus job = (JobStatus) it.next();
                if (job.hasTimingDelayConstraint()) {
                    long jobDelayTime = job.getEarliestRunTime();
                    if (jobDelayTime <= nowElapsedMillis) {
                        job.setTimingDelayConstraintSatisfied(true);
                        if (canStopTrackingJobLocked(job)) {
                            it.remove();
                        }
                        if (job.isReady()) {
                            ready = true;
                        }
                    } else if (!job.isConstraintSatisfied(2) && nextDelayTime > jobDelayTime) {
                        nextDelayTime = jobDelayTime;
                        nextDelayUid = job.getSourceUid();
                    }
                }
            }
            if (ready) {
                this.mStateChangedListener.onControllerStateChanged();
            }
            setDelayExpiredAlarmLocked(nextDelayTime, nextDelayUid);
        }
    }

    private void maybeUpdateAlarmsLocked(long delayExpiredElapsed, long deadlineExpiredElapsed, int uid) {
        if (delayExpiredElapsed < this.mNextDelayExpiredElapsedMillis) {
            setDelayExpiredAlarmLocked(delayExpiredElapsed, uid);
        }
        if (deadlineExpiredElapsed < this.mNextJobExpiredElapsedMillis) {
            setDeadlineExpiredAlarmLocked(deadlineExpiredElapsed, uid);
        }
    }

    private void setDelayExpiredAlarmLocked(long alarmTimeElapsedMillis, int uid) {
        this.mNextDelayExpiredElapsedMillis = maybeAdjustAlarmTime(alarmTimeElapsedMillis);
        updateAlarmWithListenerLocked("*job.delay*", this.mNextDelayExpiredListener, this.mNextDelayExpiredElapsedMillis, uid);
    }

    private void setDeadlineExpiredAlarmLocked(long alarmTimeElapsedMillis, int uid) {
        this.mNextJobExpiredElapsedMillis = maybeAdjustAlarmTime(alarmTimeElapsedMillis);
        updateAlarmWithListenerLocked("*job.deadline*", this.mDeadlineExpiredListener, this.mNextJobExpiredElapsedMillis, uid);
    }

    private long maybeAdjustAlarmTime(long proposedAlarmTimeElapsedMillis) {
        long earliestWakeupTimeElapsed = SystemClock.elapsedRealtime();
        if (proposedAlarmTimeElapsedMillis < earliestWakeupTimeElapsed) {
            return earliestWakeupTimeElapsed;
        }
        return proposedAlarmTimeElapsedMillis;
    }

    private void updateAlarmWithListenerLocked(String tag, OnAlarmListener listener, long alarmTimeElapsed, int uid) {
        ensureAlarmServiceLocked();
        if (alarmTimeElapsed == JobStatus.NO_LATEST_RUNTIME) {
            this.mAlarmService.cancel(listener);
            return;
        }
        this.mAlarmService.set(2, alarmTimeElapsed, -1, 0, tag, listener, null, new WorkSource(uid));
    }

    public void dumpControllerStateLocked(PrintWriter pw, int filterUid) {
        long nowElapsed = SystemClock.elapsedRealtime();
        pw.print("Alarms: now=");
        pw.print(SystemClock.elapsedRealtime());
        pw.println();
        pw.print("Next delay alarm in ");
        TimeUtils.formatDuration(this.mNextDelayExpiredElapsedMillis, nowElapsed, pw);
        pw.println();
        pw.print("Next deadline alarm in ");
        TimeUtils.formatDuration(this.mNextJobExpiredElapsedMillis, nowElapsed, pw);
        pw.println();
        pw.print("Tracking ");
        pw.print(this.mTrackedJobs.size());
        pw.println(":");
        for (JobStatus ts : this.mTrackedJobs) {
            if (ts.shouldDump(filterUid)) {
                pw.print("  #");
                ts.printUniqueId(pw);
                pw.print(" from ");
                UserHandle.formatUid(pw, ts.getSourceUid());
                pw.print(": Delay=");
                if (ts.hasTimingDelayConstraint()) {
                    TimeUtils.formatDuration(ts.getEarliestRunTime(), nowElapsed, pw);
                } else {
                    pw.print("N/A");
                }
                pw.print(", Deadline=");
                if (ts.hasDeadlineConstraint()) {
                    TimeUtils.formatDuration(ts.getLatestRunTimeElapsed(), nowElapsed, pw);
                } else {
                    pw.print("N/A");
                }
                pw.println();
            }
        }
    }
}
