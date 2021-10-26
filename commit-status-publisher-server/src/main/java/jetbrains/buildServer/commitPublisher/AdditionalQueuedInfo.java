package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdditionalQueuedInfo extends AdditionalTaskInfo {

  public AdditionalQueuedInfo(@Nullable String comment,
                              @Nullable User commentAuthor) {
    super(comment, commentAuthor, null);
  }

  @NotNull
  @Override
  public String compileQueueRelatedMessage() {
    StringBuilder result = new StringBuilder();
    if (!StringUtil.isEmptyOrSpaces(myComment)) {
      result.append(myComment);
    }
    if (myCommentAuthor != null) {
      result.append(" by ").append(myCommentAuthor.getDescriptiveName());
    }
    return result.toString();
  }
}
