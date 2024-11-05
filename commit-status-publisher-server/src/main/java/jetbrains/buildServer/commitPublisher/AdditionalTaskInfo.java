/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdditionalTaskInfo {
  protected final String myComment;
  protected final User myCommentAuthor;
  protected final BuildPromotion myReplacingPromotion;

  public AdditionalTaskInfo(@NotNull BuildPromotion targetPromotion, @Nullable String comment, @Nullable User commentAuthor) {
    this(targetPromotion, comment, commentAuthor, null);
  }

  public AdditionalTaskInfo(@NotNull BuildPromotion targetPromotion, @Nullable String comment, @Nullable User commentAuthor, @Nullable BuildPromotion replacingPromotion) {
    myComment = comment;
    myCommentAuthor = commentAuthor;
    myReplacingPromotion = replacingPromotion;
  }

  @NotNull
  public String getComment() {
    return myComment == null ? "" : myComment;
  }

  public boolean commentContains(@NotNull String substring) {
    return myComment != null && myComment.contains(substring);
  }

  @Nullable
  public User getCommentAuthor() {
    return myCommentAuthor;
  }

  @Nullable
  public BuildPromotion getReplacingPromotion() {
    return myReplacingPromotion;
  }

  public boolean isPromotionReplaced() {
    return myReplacingPromotion != null;
  }
}
