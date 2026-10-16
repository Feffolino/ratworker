package it.ratlab.ratworker.api;

/** Optional: a task that every worker knows without a tome (besides skills.innateTasks). */
public interface InnateTask {
    boolean innate();
}
