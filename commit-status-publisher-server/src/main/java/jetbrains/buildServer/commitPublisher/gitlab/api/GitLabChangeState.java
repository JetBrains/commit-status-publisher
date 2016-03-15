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

package jetbrains.buildServer.commitPublisher.gitlab.api;

import org.jetbrains.annotations.NotNull;

/**
 * Created by github.com/justmara on 15.03.2016.
 */
public enum GitLabChangeState {
    Pending("pending"),
    Running("running"),
    Success("success"),
    Failed("failed"),
    Canceled("canceled"),
    ;
    private final String myState;

    GitLabChangeState(@NotNull final String state) {
        myState = state;
    }

    @NotNull
    public String getState() {
        return myState;
    }
}
