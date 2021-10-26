package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdditionalRemovedFromQueueInfo extends AdditionalTaskInfo {

  public AdditionalRemovedFromQueueInfo(@Nullable String comment,
                                        @Nullable User commentAuthor,
                                        @Nullable BuildPromotion replacingPromotion) {
    super(comment, commentAuthor, replacingPromotion);
  }

  @NotNull
  @Override
  public String compileQueueRelatedMessage() {
    StringBuilder result = new StringBuilder(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE);
    if (myCommentAuthor != null) {
      result.append(" by ").append(myCommentAuthor.getDescriptiveName());
    }
    if (!StringUtil.isEmptyOrSpaces(myComment)) {
      result.append(": ").append(myComment);
    }
    if (isPromotionReplaced()) {
      result.append(". Link leads to the actual build");
    }
    return result.toString();
  }

}
