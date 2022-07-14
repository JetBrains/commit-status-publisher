package jetbrains.buildServer.commitPublisher;

class ValueWithTTL<T> {
  public static final ValueWithTTL<Boolean> OUTDATED_CACHE_VALUE = new ValueWithTTL<Boolean>(null, -1L);

  private final T myValue;
  private final long myExpirationTime;

  public ValueWithTTL(T value, long expirationTime) {
    myValue = value;
    myExpirationTime = expirationTime;
  }

  public boolean isAlive() {
    return System.currentTimeMillis() <= myExpirationTime;
  }

  public T getValue() {
    return myValue;
  }
}
