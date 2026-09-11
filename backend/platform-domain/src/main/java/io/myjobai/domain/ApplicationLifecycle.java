package io.myjobai.domain;

import java.time.Instant;
import java.util.*;

public final class ApplicationLifecycle {
  private ApplicationLifecycle() {}

  public enum State {
    DISCOVERED,
    SAVED,
    SHORTLISTED,
    PREPARING,
    READY_TO_APPLY,
    APPLIED,
    OUTREACH_PREPARED,
    OUTREACH_SENT,
    REPLIED,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN
  }

  private static final Map<State, Set<State>> TRANSITIONS =
      Map.ofEntries(
          Map.entry(State.DISCOVERED, Set.of(State.SAVED, State.SHORTLISTED)),
          Map.entry(State.SAVED, Set.of(State.SHORTLISTED, State.PREPARING)),
          Map.entry(State.SHORTLISTED, Set.of(State.PREPARING)),
          Map.entry(State.PREPARING, Set.of(State.READY_TO_APPLY, State.OUTREACH_PREPARED)),
          Map.entry(State.READY_TO_APPLY, Set.of(State.APPLIED, State.OUTREACH_PREPARED)),
          Map.entry(State.APPLIED, Set.of(State.OUTREACH_PREPARED, State.REPLIED, State.INTERVIEW)),
          Map.entry(State.OUTREACH_PREPARED, Set.of(State.OUTREACH_SENT, State.APPLIED)),
          Map.entry(State.OUTREACH_SENT, Set.of(State.REPLIED, State.INTERVIEW)),
          Map.entry(State.REPLIED, Set.of(State.INTERVIEW, State.OFFER)),
          Map.entry(State.INTERVIEW, Set.of(State.OFFER)),
          Map.entry(State.OFFER, Set.of(State.WITHDRAWN)));

  public static void requireTransition(State from, State to) {
    boolean terminal = from == State.REJECTED || from == State.WITHDRAWN;
    if (terminal
        || from == to
        || !(to == State.WITHDRAWN
            || to == State.REJECTED
            || TRANSITIONS.getOrDefault(from, Set.of()).contains(to)))
      throw DomainException.conflict("Illegal application transition: " + from + " → " + to);
  }

  public record Application(
      UUID id, UUID userId, UUID jobId, State state, Instant createdAt, Instant updatedAt) {}

  public record Event(
      UUID id, UUID userId, UUID applicationId, State from, State to, String note, Instant at) {}
}
