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

package jetbrains.buildServer.commitPublisher.github.api.impl;

import jetbrains.buildServer.commitPublisher.github.api.GitCommitStatusFormatter;
import jetbrains.buildServer.commitPublisher.github.api.GitChangeState;
import org.jetbrains.annotations.NotNull;

/**
 * Created by github.com/justmara on 15.03.2016.
 */
public class GitHubCommitStatusFormatterImpl implements GitCommitStatusFormatter {

    @NotNull
    public String getStatus(GitChangeState status) {
        switch (status)
        {
            case Running:
            case Pending:
                return "pending";
            case Error:
                return "error";
            case Success:
                return "success";
            case Canceled:
            case Failure:
            default:
                return "failure";
        }
    }
}
