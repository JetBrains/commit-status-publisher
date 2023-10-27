package jetbrains.buildServer.commitPublisher;

public enum BuildReason {
  UNKNOWN,
  TRIGGERED_DIRECTLY,
  TRIGGERED_AS_DEPENDENCY,
}
