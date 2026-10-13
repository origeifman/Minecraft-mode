package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.bridge.Baritone;

/**
 * A unit of in-game work, advanced once per client tick. Tasks may run one child task at a time
 * (e.g. "gather" needs a pickaxe, so it runs a "craft" child first). onStart must be safe to call
 * again after a pause, because interruptions (fleeing, eating) restart the task via resume().
 */
public abstract class Task {
    public enum Status { RUNNING, DONE, FAILED }

    protected Agent agent;
    private Status status = Status.RUNNING;
    private String message = "";
    protected int ticks;
    private Task child;
    private Task finishedChild;

    public final Status status() { return status; }

    public final String message() { return message; }

    public abstract String describe();

    /** Background tasks (following) hand control straight back to the planner. */
    public boolean background() { return false; }

    protected abstract void onStart(Agent a);

    protected abstract void tick(Agent a);

    /** Cleanup hook, called exactly once when the task stops running for any reason. */
    protected void onEnd(Agent a) {}

    public final void begin(Agent a) {
        this.agent = a;
        onStart(a);
    }

    public final void update(Agent a) {
        if (status != Status.RUNNING) return;
        ticks++;
        tick(a);
    }

    public void pause(Agent a) {
        Baritone.cancel();
        if (child != null) child.pause(a);
    }

    public void resume(Agent a) {
        if (child != null) child.resume(a);
        else onStart(a);
    }

    public final void cancel(Agent a) {
        if (child != null) child.cancel(a);
        Baritone.cancel();
        if (status == Status.RUNNING) {
            status = Status.FAILED;
            message = "cancelled";
            onEnd(a);
        }
    }

    protected final void done(String msg) { finish(Status.DONE, msg); }

    protected final void fail(String msg) { finish(Status.FAILED, msg); }

    private void finish(Status s, String msg) {
        if (status != Status.RUNNING) return;
        status = s;
        message = msg;
        if (child != null) { child.cancel(agent); child = null; }
        if (agent != null) onEnd(agent);
    }

    protected final void startChild(Agent a, Task c) {
        child = c;
        finishedChild = null;
        c.begin(a);
    }

    /** Drives the child. @return true while it is still running (caller should return). */
    protected final boolean childRunning(Agent a) {
        if (child == null) return false;
        child.update(a);
        if (child.status == Status.RUNNING) return true;
        finishedChild = child;
        child = null;
        return false;
    }

    /** The child that finished during the last childRunning() call, once. */
    protected final Task takeFinished() {
        Task f = finishedChild;
        finishedChild = null;
        return f;
    }

    protected final boolean hasChild() { return child != null; }
}
