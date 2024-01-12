

package jetbrains.buildServer.commitPublisher.reports;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.PublisherManager;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApiAuthenticationType;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.healthStatus.*;
import org.jetbrains.annotations.NotNull;

public class DeprecatedAuthReport extends HealthStatusReport {

  private static final String REPORT_TYPE = "CommitStatusPublisherDeprecatedAuth";
  private static final String DISPLAY_NAME
          = "Commit Status Publisher build feature uses username/password authentication that is now deprecated in GitHub";
  private static final ItemCategory CATEGORY
          = new ItemCategory(REPORT_TYPE + "Category", DISPLAY_NAME, ItemSeverity.WARN);

  private final PublisherManager myPublisherManager;

  public DeprecatedAuthReport(@NotNull PublisherManager publisherManager) {
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
  public boolean canReportItemsFor(@NotNull HealthStatusScope healthStatusScope) {
    return healthStatusScope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {

    for (SBuildType bt : scope.getBuildTypes()) {
      Collection<SBuildFeatureDescriptor> features = bt.getBuildFeaturesOfType(CommitStatusPublisherFeature.TYPE);
      for (SBuildFeatureDescriptor feature: features) {
        if (bt.isEnabled(feature.getId())) {
          Map<String, String> params = feature.getParameters();
          String publisherId = params.get(Constants.PUBLISHER_ID_PARAM);
          if (publisherId == null || !publisherId.equals(Constants.GITHUB_PUBLISHER_ID))
            continue;
          CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
          if (null != settings && GitHubApiAuthenticationType.PASSWORD_AUTH.getValue().equals(params.get(Constants.GITHUB_AUTH_TYPE))) {
            String identity = REPORT_TYPE + "_BT_" + bt.getInternalId() + "_FEATURE_" + feature.getId();
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("buildType", bt);
            additionalData.put("publisherType", settings.getName());
            consumer.consumeForBuildType(bt, new HealthStatusItem(identity, CATEGORY, additionalData));
          }
        }
      }
    }
  }
}