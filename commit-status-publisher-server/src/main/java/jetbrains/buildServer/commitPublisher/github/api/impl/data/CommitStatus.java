

package jetbrains.buildServer.commitPublisher.github.api.impl.data;

import org.jetbrains.annotations.Nullable;

/**
* Created by Eugene Petrenko (eugene.petrenko@gmail.com)
* Date: 04.03.13 22:33
*/
@SuppressWarnings("UnusedDeclaration")
public class CommitStatus {
  @Nullable public final String state;
  @Nullable public final String target_url;
  @Nullable public final String description;
  @Nullable public final String context;

  public CommitStatus(@Nullable String state, @Nullable String target_url, @Nullable String description, @Nullable String context) {
    this.state = state;
    this.target_url = target_url;
    this.description = truncateStringValueWithDotsAtEnd(description, 140);
    this.context = context;
  }

  @Nullable
  private static String truncateStringValueWithDotsAtEnd(@Nullable final String str, final int maxLength) {
    if (str == null) return null;
    if (str.length() > maxLength) {
      return str.substring(0, maxLength - 2) + "\u2026";
    }
    return str;
  }
}