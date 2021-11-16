package jetbrains.buildServer.commitPublisher;

import com.google.common.util.concurrent.Striped;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildPromotionsCache {
  private static final String MAX_REVISIONS_PROPERTY_NAME = "teamcity.commitStatusPublisher.promotionsCache.maxRevisions";
  private static final String MAX_PROMOTIONS_PROPERTY_NAME = "teamcity.commitStatusPublisher.promotionsCache.maxPromotions";
  private static final int MAX_REVISIONS_TO_STORE = 128;
  private static final int MAX_PROMOTIONS_TO_STORE = 8;

  private final Map<String, Map<Long, BuildPromotion>> myCache;
  private final int myMaxPromotions;
  private final Striped<Lock> myLocks = Striped.lazyWeakLock(100);

  public BuildPromotionsCache() {
    this(TeamCityProperties.getInteger(MAX_REVISIONS_PROPERTY_NAME, MAX_REVISIONS_TO_STORE),
         TeamCityProperties.getInteger(MAX_PROMOTIONS_PROPERTY_NAME, MAX_PROMOTIONS_TO_STORE));
  }

  public BuildPromotionsCache(int maxRevisions, int maxPromotions) {
    myCache = new LinkedHashMap<String, Map<Long, BuildPromotion>>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > maxRevisions;
      }
    };
    myMaxPromotions = maxPromotions;
  }

  public void put(@NotNull String revision, @NotNull BuildPromotion buildPromotion) {
    Lock revisionLock = myLocks.get(revision);
    revisionLock.lock();
    try {
      Lock promotionLock = myLocks.get(buildPromotion.getId());
      promotionLock.lock();
      try {
        myCache.computeIfAbsent(revision, k -> new LinkedHashMap<Long, BuildPromotion>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > myMaxPromotions;
          }
        }).putIfAbsent(buildPromotion.getId(), buildPromotion);
      } finally {
        promotionLock.unlock();
      }
    } finally {
      revisionLock.unlock();
    }
  }

  @Nullable
  public BuildPromotion getLast(@NotNull String revision) {
    Lock revisionLock = myLocks.get(revision);
    revisionLock.lock();
    try {
      Map<Long, BuildPromotion> buildPromotions = myCache.get(revision);
      if (buildPromotions == null) {
        return null;
      }
      return (BuildPromotion)buildPromotions.values().toArray()[buildPromotions.size() - 1];
    } finally {
      revisionLock.unlock();
    }
  }

  @Nullable
  public BuildPromotion remove(@NotNull String revision, long id) {
    Lock revisionLock = myLocks.get(revision);
    revisionLock.lock();
    try {
      Map<Long, BuildPromotion> buildPromotions = myCache.get(revision);
      if (buildPromotions == null) {
        return null;
      }
      Lock promotionLock = myLocks.get(id);
      promotionLock.lock();
      try {
        BuildPromotion removedValue = buildPromotions.remove(id);
        if (buildPromotions.isEmpty()) {
          myCache.remove(revision);
        }
        return removedValue;
      } finally {
        promotionLock.unlock();
      }
    } finally {
      revisionLock.unlock();
    }
  }
}
