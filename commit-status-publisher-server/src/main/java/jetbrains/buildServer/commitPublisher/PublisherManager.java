package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.ExtensionsCollection;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PublisherManager {

  private final ExtensionsCollection<CommitStatusPublisherSettings> myPublisherSettings;

  public PublisherManager(@NotNull ExtensionHolder extensionHolder) {
    myPublisherSettings = extensionHolder.getExtensionsCollection(CommitStatusPublisherSettings.class);
  }

  @Nullable
  public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
    String publisherId = params.get(Constants.PUBLISHER_ID_PARAM);
    if (publisherId == null)
      return null;
    CommitStatusPublisherSettings settings = findSettings(publisherId);
    if (settings == null)
      return null;
    return settings.createPublisher(buildType, buildFeatureId, params);
  }

  @Nullable
  public CommitStatusPublisherSettings findSettings(@NotNull String publisherId) {
    return myPublisherSettings.getExtensions().stream().filter(s -> publisherId.equals(s.getId())).findFirst().orElse(null);
  }

  @NotNull
  List<CommitStatusPublisherSettings> getAllPublisherSettings() {
    List<CommitStatusPublisherSettings> settings = new ArrayList<CommitStatusPublisherSettings>();
    for (CommitStatusPublisherSettings s : myPublisherSettings.getExtensions()) {
      if (s.isEnabled())
        settings.add(s);
    }
    Collections.sort(settings, new Comparator<CommitStatusPublisherSettings>() {
      public int compare(CommitStatusPublisherSettings o1, CommitStatusPublisherSettings o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return settings;
  }
}
