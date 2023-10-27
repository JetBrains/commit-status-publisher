package jetbrains.buildServer.commitPublisher.reports;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.BuildReason;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher;
import jetbrains.buildServer.commitPublisher.PublisherManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.serverSide.oauth.space.SpaceFeatures;
import org.jetbrains.annotations.NotNull;

public class UnconditionalPublishingReport extends HealthStatusReport {

  private static final String REPORT_TYPE = "CommitStatusPublisherUnconditional";
  private static final String DISPLAY_NAME = "Unconditional commit status publishing is active";
  private static final ItemCategory CATEGORY = new ItemCategory(REPORT_TYPE + "Category", DISPLAY_NAME, ItemSeverity.INFO);

  @NotNull
  private final PublisherManager myPublisherManager;

  public UnconditionalPublishingReport(@NotNull PublisherManager publisherManager) {
    myPublisherManager = publisherManager;
  }

  @NotNull
  @Override
  public String getType() {
    return REPORT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return scope.isItemWithSeverityAccepted(ItemSeverity.INFO);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer resultConsumer) {
    for (SBuildType buildType : scope.getBuildTypes()) {
      if (!SpaceFeatures.forScope(buildType).reportUnconditionalPublishing()) {
        continue;
      }

      if (myPublisherManager.isFeatureLessPublishingPossible(buildType, BuildReason.TRIGGERED_DIRECTLY)) {
        final Map<String, CommitStatusPublisher> configuredPublishers = myPublisherManager.createConfiguredPublishers(buildType);
        final Map<String, CommitStatusPublisher> supplementaryPublishers = myPublisherManager.createSupplementaryPublishers(buildType, configuredPublishers);
        if (!supplementaryPublishers.isEmpty()) {
          final String identity = REPORT_TYPE + "_BT_" + buildType.getInternalId();
          final Map<String, Object> additionalData = Collections.singletonMap("buildType", buildType);
          resultConsumer.consumeForBuildType(buildType, new HealthStatusItem(identity, CATEGORY, additionalData));
        }
      }
    }
  }
}
