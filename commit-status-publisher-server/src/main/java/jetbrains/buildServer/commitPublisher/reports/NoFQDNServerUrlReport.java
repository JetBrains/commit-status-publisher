package jetbrains.buildServer.commitPublisher.reports;

import java.util.*;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherFeature;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisherSettings;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.PublisherManager;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;

public class NoFQDNServerUrlReport extends HealthStatusReport {

  private static final Pattern URL_WITH_FQDN_PATTERN = Pattern.compile("[a-z]+://[^\\.:/]+\\.(.+)");
  private static final String REPORT_TYPE = "CommitStatusPublisherNoFQDNServerUrl";
  private static final String DISPLAY_NAME
          = "Commit Status Publisher build feature may not work correctly when TeamCity Server URL does not refer to a fully qualified domain name";
  private static final ItemCategory CATEGORY
          = new ItemCategory(REPORT_TYPE + "Category", DISPLAY_NAME, ItemSeverity.WARN);

  private final WebLinks myLinks;
  private final PublisherManager myPublisherManager;

  public NoFQDNServerUrlReport(@NotNull PublisherManager publisherManager, @NotNull WebLinks links) {
    myLinks = links;
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
          if (publisherId == null)
            continue;
          CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
          if (null != settings && settings.isFQDNTeamCityUrlRequired()) {
            String rootUrl = myLinks.getRootUrlByProjectInternalId(bt.getProjectId());
            if(!URL_WITH_FQDN_PATTERN.matcher(rootUrl).matches()) {
              String identity = REPORT_TYPE + "_BT_" + bt.getInternalId() + "_FEATURE_" + feature.getId();
              Map<String, Object> additionalData = new HashMap<>();
              additionalData.put("rootUrl", rootUrl);
              additionalData.put("buildType", bt);
              additionalData.put("publisherType", settings.getName());
              consumer.consumeForBuildType(bt, new HealthStatusItem(identity, CATEGORY, additionalData));
            }
          }
        }
      }
    }
  }
}
