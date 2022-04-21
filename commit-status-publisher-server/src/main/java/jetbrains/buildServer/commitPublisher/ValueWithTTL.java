package jetbrains.buildServer.commitPublisher;

class ValueWithTTL<T> {
  public static final ValueWithTTL<Boolean> OUTDATED_CACHE_VALUE = new ValueWithTTL<Boolean>(null, -1L);

  private final T myValue;
  private final long myTtl;

  public ValueWithTTL(T value, long ttl) {
    myValue = value;
    myTtl = ttl;
  }

  public boolean isAlive() {
    return System.currentTimeMillis() <= myTtl;
  }

  public T getValue() {
    return myValue;
  }
}
