/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher.github;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;


public class BuildSizeChange {
  private static final Logger LOG = Logger.getInstance(ChangeStatusUpdater.class.getName());
  private static final UpdateChangesConstants C = new UpdateChangesConstants();

  private class SizeChange {

    SizeChange(long sizeChangeBytes, double sizeChangePct) {
      bytes = sizeChangeBytes;
      pct = sizeChangePct;
    }
    private long bytes;
    private double pct;
  }

  private static final long CHANGE_DISABLED = -1;
  private Vector<Pattern> desiredFiles = new Vector<Pattern>();
  private HashMap<String, SizeChange> artChanges = new HashMap<String, SizeChange>();
  private long maxBytesChange;
  private double maxPctChange;
  private boolean failOnSize = false;

  BuildSizeChange(@NotNull Map<String, String> params) {
    String artifacts = params.get(C.getArtifactsKey());
    if (artifacts != null && !artifacts.isEmpty()) {
      for (String file : params.get(C.getArtifactsKey()).split("\n")) {
        desiredFiles.add(Pattern.compile(file));
      }
    }

    String bytesKey = params.get(C.getBytesKey());
    String pctKey   = params.get(C.getPctKey());

    maxBytesChange = (bytesKey == null || bytesKey.isEmpty()) ? CHANGE_DISABLED : Long.parseLong(bytesKey);
    maxPctChange =   (pctKey == null   || pctKey.isEmpty())     ? CHANGE_DISABLED : Double.parseDouble(pctKey);
    String fos = params.get(C.getFailOnSizeKey());
    if (fos != null && !fos.isEmpty()) {
      failOnSize = fos.equals("true");
    }
  }

  @NotNull
  private String getFriendlySize(final long bytes) {
    if (bytes < 1024 && bytes > -1024)
      return String.format("%+d B", bytes);

    String[] suffix = {"KiB", "MiB", "GiB"};
    double finalBytes = bytes / 1024.0;
    int index = 0;
    while (finalBytes > 1024.0 && index < suffix.length) {
      finalBytes /= 1024.0;
      index++;
    }
    return String.format("%+.2f %s", finalBytes, suffix[index]);
  }

  private boolean contains(String file) {
    for(Pattern filePattern : desiredFiles) {
      if (filePattern.matcher(file).matches()) return true;
    }
    return false;
  }

  private SBuild getDefaultBuild(@NotNull SBuild build) {
    String defaultBranch = build.getParametersProvider().get("vcsroot.branch");
    SBuildType buildType = build.getBuildType();

    List<SFinishedBuild> builds = buildType.getHistory();
    for (SFinishedBuild fBuild : builds) {
      Branch fBranch = fBuild.getBranch();
      if (fBranch != null &&
          fBranch.getDisplayName().equals(defaultBranch) &&
          fBuild.isArtifactsExists() &&
          fBuild != build) {
        return fBuild;
      }
    }
    LOG.info("Unable to find default build to compare sizes");
    return null;
  } 

  private void calculateSizeDiff(@NotNull SBuild build) {

    SBuild defaultBuild = getDefaultBuild(build);

    if (defaultBuild == null) return;
      
    HashMap<String, Long> artSizes = new HashMap<String, Long>();
    BuildArtifact defaultArtRoot = defaultBuild.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT).getRootArtifact();

    for (BuildArtifact buildArt : defaultArtRoot.getChildren()) {
      LOG.info("Master Artifact: " + buildArt.getSize() + " - " + buildArt.getName());
      if (!contains(buildArt.getName())) continue;
      artSizes.put(buildArt.getName(), buildArt.getSize());
    }

    BuildArtifact prArtRoot = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT).getRootArtifact();
    for (BuildArtifact buildArt : prArtRoot.getChildren()) {
      LOG.info("PR Artifact: " + buildArt.getSize() + " - " + buildArt.getName());
      String artName = buildArt.getName();
      if (!contains(artName)) continue;

      if (artSizes.get(artName) != null) {
        long sizeChange = (buildArt.getSize() - artSizes.get(artName));
        double sizeChangePct = 0.0;
        if (artSizes.get(artName) != 0)
          sizeChangePct = (sizeChange / artSizes.get(artName).doubleValue()) * 100;

        artChanges.put(artName, new SizeChange(sizeChange, sizeChangePct));
      }
      else {
        artChanges.put(artName, null);
      }
    }
  }

  boolean bytesExceeded(long bytes) {
    return maxBytesChange != CHANGE_DISABLED  && bytes >= maxBytesChange;
  }

  boolean pctExceeded(double pct) {
    return maxPctChange != CHANGE_DISABLED  && pct >= maxPctChange;
  }

  String boldText(String text) {
    return "**" + text + "**";
  }

  @NotNull
  public boolean hasSizeFailure(@NotNull SBuild build) {
    if (!failOnSize) return false;

    calculateSizeDiff(build);

    for (Map.Entry<String, SizeChange> e : artChanges.entrySet()) {
      if (e.getValue() == null) continue;
      if (bytesExceeded(e.getValue().bytes)) return true;
      if (pctExceeded(e.getValue().pct)) return true;
    }
    return false;
  }

  @NotNull
  public String getComment(@NotNull SBuild build) {
    calculateSizeDiff(build);

    final StringBuilder comment = new StringBuilder();

    comment.append("Artifact size changes:\n");
    for (Map.Entry<String, SizeChange> e : artChanges.entrySet()) {
      String changeStr;
      if (e.getValue() != null) {
        String bytesChange = getFriendlySize(e.getValue().bytes);
        if (bytesExceeded(e.getValue().bytes)) bytesChange = boldText(bytesChange);
        String pctChange = String.format("%+.2f%%", e.getValue().pct);
        if (pctExceeded(e.getValue().pct)) pctChange = boldText(pctChange);
        changeStr = String.format("%s: %s (%s)\n", e.getKey(), bytesChange, pctChange);
      } else {
        changeStr = String.format("%s: New Artifact\n", e.getKey());
      }
      comment.append(changeStr);
    }

    return comment.toString();
  }


}
