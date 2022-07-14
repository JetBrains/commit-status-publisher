package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommitStatusesCache<T> {
  private static final int CACHE_MAX_SIZE_DEFAULT_VALUE = 256;
  private static final String CACHE_MAX_SIZE_PARAMETER = "teamcity.commitStatusPublisher.statusCache.maxSize";
  private static final String CACHE_VALUE_TTL_PARAMETER = "teamcity.commitStatusPublisher.statusCache.ttl";
  private static final long CACHE_VALUE_TTL_DEFAULT_VALUE_MS = 300_000L;
  private static final String CACHE_FEATURE_TOGGLE_PARAMETER = "teamcity.commitStatusPublisher.statusCache.enabled";

  private final Map<String, ValueWithTTL<T>> myCache = new HashMap<>();
  private final Striped<Lock> myCacheLocks = Striped.lazyWeakLock(256);
  private final ReentrantReadWriteLock myWholeCacheLock = new ReentrantReadWriteLock();

  @Nullable
  private T getStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix) {
    ReentrantReadWriteLock.ReadLock lock = myWholeCacheLock.readLock();
    lock.lock();
    try {
      ValueWithTTL<T> value = myCache.get(buildKey(revision, prefix));
      if (value != null && value.isAlive()) {
        return value.getValue();
      }
      return null;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns status from the cache. If there was no status in the cache for provided revision, it tries to load a status using provided supplier
   * @param revision key for the cache
   * @param prefix business logic related key prefix for the cache
   * @param statusLoader status supplier, that used in case when status was not found in cache
   * @return status related to the provided revision
   */
  @Nullable
  public T getStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix, @NotNull Supplier<T> statusLoader) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return null;

    T status = getStatusFromCache(revision, prefix);
    if (status != null) {
      return status;
    }
    Lock lock = myCacheLocks.get(revision.getRevision());
    lock.lock();
    try {
      status = getStatusFromCache(revision, prefix);
      if (status != null) {
        return status;
      }

      T loadedStatus = statusLoader.get();
      if (loadedStatus != null) {
        putStatusToCache(revision, prefix, loadedStatus);
      }
      return loadedStatus;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns status from the cache. If there was no status in the cache for provided revision, it tries to load a batch of statuses using provided supplier and put them to cache
   * @param revision key for the cache
   * @param prefix business logic related key prefix for the cache
   * @param batchStatusLoader statuses supplier, that used in case when status was not found in cache
   * @param prefixProvider method that extracts prefix from the received status
   * @return status related to the provided revision
   */
  @Nullable
  public T getStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix,
                              @NotNull Supplier<Collection<T>> batchStatusLoader, @NotNull Function<T, String> prefixProvider) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return null;

    T status = getStatusFromCache(revision, prefix);
    if (status != null) {
      return status;
    }
    Lock lock = myCacheLocks.get(revision.getRevision());
    lock.lock();
    try {
      status = getStatusFromCache(revision, prefix);
      if (status != null) {
        return status;
      }

      Collection<T> loadedStatuses = batchStatusLoader.get();
      if (loadedStatuses != null && !loadedStatuses.isEmpty()) {
        putStatusesToCache(revision, loadedStatuses, prefixProvider);
      }
      return getStatusFromCache(revision, prefix);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Puts new value to the cache
   * @param revision key for the cache
   * @param prefix business logic related key prefix for the cache
   * @param status new value to be added to the cache
   * @return previous value in cache or null
   */
  private void putStatusToCache(@NotNull BuildRevision revision, @Nullable String prefix, @NotNull T status) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    ReentrantReadWriteLock.WriteLock lock = myWholeCacheLock.writeLock();
    lock.lock();
    try {
      myCache.put(buildKey(revision, prefix), new ValueWithTTL<>(status, getExpirationTime()));
    } finally {
      lock.unlock();
    }
    cleanupCache();
  }

  private void putStatusesToCache(@NotNull BuildRevision revision, @NotNull Collection<T> statuses, Function<T, String> prefixProvider) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    ReentrantReadWriteLock.WriteLock lock = myWholeCacheLock.writeLock();
    lock.lock();
    try {
      for (T status : statuses) {
        String prefix = prefixProvider.apply(status);
        String key = buildKey(revision, prefix);
        myCache.put(key, new ValueWithTTL<>(status, getExpirationTime()));
      }
    } finally {
      lock.unlock();
    }
    cleanupCache();
  }

  /**
   * Removes value from the cache
   * @param revision key for the cache
   * @param prefix business logic related key prefix for the cache
   * @return removed from the cache value
   */
  public void removeStatusFromCache(@NotNull BuildRevision revision, @Nullable String prefix) {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    ReentrantReadWriteLock.WriteLock lock = myWholeCacheLock.writeLock();
    lock.lock();
    try {
      myCache.remove(buildKey(revision, prefix));
    } finally {
      lock.unlock();
    }
  }

  private void cleanupCache() {
    if (!TeamCityProperties.getBooleanOrTrue(CACHE_FEATURE_TOGGLE_PARAMETER)) return;

    if (TeamCityProperties.getInteger(CACHE_MAX_SIZE_PARAMETER, CACHE_MAX_SIZE_DEFAULT_VALUE) > myCache.size()) return;

    ReentrantReadWriteLock.WriteLock lock = myWholeCacheLock.writeLock();
    lock.lock();
    try {
      if (TeamCityProperties.getInteger(CACHE_MAX_SIZE_PARAMETER, CACHE_MAX_SIZE_DEFAULT_VALUE) > myCache.size()) return;

      myCache.values().removeIf(val -> !val.isAlive());
    } finally {
      lock.unlock();
    }
  }

  @NotNull
  private String buildKey(@NotNull BuildRevision revision, @Nullable String prefix) {
    StringBuilder key = new StringBuilder();
    if (prefix != null) key.append(revision.getRevision()).append(":");
    key.append(revision.getRoot().getId()).append(":").append(revision.getRevision());
    return key.toString();
  }

  private long getExpirationTime() {
    return System.currentTimeMillis() + TeamCityProperties.getIntervalMilliseconds(CACHE_VALUE_TTL_PARAMETER, CACHE_VALUE_TTL_DEFAULT_VALUE_MS);
  }
}
