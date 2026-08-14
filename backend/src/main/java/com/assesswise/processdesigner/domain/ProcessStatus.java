package com.assesswise.processdesigner.domain;

/** Lifecycle of a process: created (current state only) vs. analysed (future state generated). */
public enum ProcessStatus {
    CURRENT_ONLY,
    ANALYZED
}
