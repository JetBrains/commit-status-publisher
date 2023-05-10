/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.commitPublisher.Constants;
import jetbrains.buildServer.commitPublisher.LoggerUtil;
import jetbrains.buildServer.commitPublisher.PublisherException;
import jetbrains.buildServer.commitPublisher.Repository;
import jetbrains.buildServer.commitPublisher.github.api.GitHubApi;
import jetbrains.buildServer.commitPublisher.github.api.GitHubChangeState;
import jetbrains.buildServer.commitPublisher.github.api.impl.data.*;
import jetbrains.buildServer.http.SimpleCredentials;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.http.HttpMethod;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicStatusLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;
import static org.apache.http.HttpVersion.HTTP_1_1;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 * @author Tomaz Cerar
 *         Date: 05.09.12 23:39
 */
public abstract class GitHubApiImpl implements GitHubApi {
  private static final Pattern PULL_REQUEST_BRANCH = Pattern.compile("/?refs/pull/(\\d+)/(.*)");
  private static final String MSG_PROXY_OR_PERMISSIONS = "Please check if the error is not returned by a proxy or caused by the lack of permissions.";
  private static final String MSG_NOT_FOUND = "Repository not found. Make sure the repository exists and the URL is correct.";

  private final HttpClientWrapper myClient;
  private final GitHubApiPaths myUrls;
  private final Gson myGson;

  public GitHubApiImpl(@NotNull final HttpClientWrapper client,
                       @NotNull final GitHubApiPaths urls
  ) {
    myClient = client;
    myUrls = urls;
    myGson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
  }

  @Nullable
  private static String getPullRequestId(@NotNull String repoName,
                                         @NotNull String branchName) {
    final Matcher matcher = PULL_REQUEST_BRANCH.matcher(branchName);
    if (!matcher.matches()) {
      LOG.debug("Branch " + branchName + " for repo " + repoName + " does not look like pull request");
      return null;
    }

    final String pullRequestId = matcher.group(1);
    if (pullRequestId == null) {
      LOG.debug("Branch " + branchName + " for repo " + repoName + " does not contain pull request id");
      return null;
    }
    return pullRequestId;
  }

  public void testConnection(@NotNull final Repository repo) throws PublisherException {
    final String uri = myUrls.getRepoInfo(repo.owner(), repo.repositoryName());
    RepoInfo repoInfo;
    try {
      repoInfo = processResponse(uri, RepoInfo.class, true);
    } catch (Throwable ex) {
      throw new PublisherException(String.format("Could not retrieve information about the \"%s\" repository", repo.url()), ex);
    }

    checkPermissions(repo, repoInfo);
  }

  protected void checkPermissions(@NotNull final Repository repo, @NotNull RepoInfo repoInfo) throws PublisherException {
    if (null == repoInfo.name || null == repoInfo.permissions) {
      throw new PublisherException(String.format("Repository \"%s\" is inaccessible", repo.url()));
    }
    if (!repoInfo.permissions.push) {
      throw new PublisherException(String.format("There is no push access to the repository \"%s\"", repo.url()));
    }
  }

  public CombinedCommitStatus readChangeCombinedStatus(@NotNull final String repoOwner,
                                                       @NotNull final String repoName,
                                                       @NotNull final String hash,
                                                       @Nullable final Integer perPage,
                                                       @Nullable final Integer page) throws IOException {
    final String statusUrl = myUrls.getCombinedStatusUrl(repoOwner, repoName, hash, perPage, page);

    final HttpMethod method = HttpMethod.GET;
    LoggerUtil.logRequest(Constants.GITHUB_PUBLISHER_ID, method, statusUrl, null);

    final AtomicReference<CombinedCommitStatus> status = new AtomicReference<>();
    final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    IOGuard.allowNetworkCall(() -> {
      myClient.get(statusUrl, authenticationCredentials(), defaultHeaders(),
                   success -> {
                     String json = success.getBodyAsString();
                     if (StringUtil.isEmptyOrSpaces(json)) {
                       logFailedResponse(HttpMethod.GET, statusUrl, null, success);
                       exceptionRef.set(new IOException(getErrorMessage(success, "Empty response.")));
                       return;
                     }
                     CombinedCommitStatus combinedCommitStatus;
                     try {
                       combinedCommitStatus = myGson.fromJson(json, CombinedCommitStatus.class);
                     } catch (JsonSyntaxException e) {
                       exceptionRef.set(new PublisherException("GitHub publisher can not parse malformed json", e));
                       return;
                     }
                     if (null == combinedCommitStatus) {
                       exceptionRef.set(new PublisherException("GitHub publisher fails to parse a response"));
                     } else {
                       status.set(combinedCommitStatus);
                     }
                   },
                   response -> {
                     logFailedResponse(method, statusUrl, null, response);
                     exceptionRef.set(new IOException(getErrorMessage(response, null)));
                   },
                   e -> exceptionRef.set(e));
    });

    final Exception ex;
    if ((ex = exceptionRef.get()) != null) {
      if (ex instanceof IOException) {
        throw (IOException) ex;
      } else {
        throw new IOException(ex);
      }
    }

    return status.get();
  }

  private Map<String, String> defaultHeaders() {
    final Map<String, String> result = new LinkedHashMap<String, String>();
    result.put(HttpHeaders.ACCEPT_ENCODING, "UTF-8");
    result.put(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());

    return result;
  }

  public void setChangeStatus(@NotNull final String repoOwner,
                              @NotNull final String repoName,
                              @NotNull final String hash,
                              @NotNull final GitHubChangeState status,
                              @NotNull final String targetUrl,
                              @NotNull final String description,
                              @Nullable final String context) throws IOException {

    final String url = myUrls.getStatusUrl(repoOwner, repoName, hash);
    final String entity = myGson.toJson(new CommitStatus(status.getState(), targetUrl, description, context));

    final HttpMethod method = HttpMethod.POST;
    LoggerUtil.logRequest(Constants.GITHUB_PUBLISHER_ID, method, url, entity);

    final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    IOGuard.allowNetworkCall(() -> {
      myClient.post(
        url, authenticationCredentials(), defaultHeaders(),
        entity, ContentType.APPLICATION_JSON.getMimeType(), ContentType.APPLICATION_JSON.getCharset(),
        response -> {
        },
        response -> {
          logFailedResponse(method, url, entity, response);
          String additionalComment = response.getStatusCode() == HttpStatus.SC_NOT_FOUND ? MSG_NOT_FOUND : MSG_PROXY_OR_PERMISSIONS;
          exceptionRef.set(new IOException(getErrorMessage(response, additionalComment)));
        },
        e -> exceptionRef.set(e));
    });

    final Exception ex;
    if ((ex = exceptionRef.get()) != null) {
      if (ex instanceof IOException) {
        throw (IOException) ex;
      } else {
        throw new IOException(ex);
      }
    }
  }

  public boolean isPullRequestMergeBranch(@NotNull String branchName) {
    final Matcher match = PULL_REQUEST_BRANCH.matcher(branchName);
    return match.matches() && "merge".equals(match.group(2));
  }

  @Nullable
  public String findPullRequestCommit(@NotNull String repoOwner,
                                      @NotNull String repoName,
                                      @NotNull String branchName) throws IOException, PublisherException {

    final String pullRequestId = getPullRequestId(repoName, branchName);
    if (pullRequestId == null) return null;

    //  /repos/:owner/:repo/pulls/:number

    final String requestUrl = myUrls.getPullRequestInfo(repoOwner, repoName, pullRequestId);
    final PullRequestInfo pullRequestInfo = processResponse(requestUrl, PullRequestInfo.class, false);

    final RepoRefInfo head = pullRequestInfo.head;
    if (head != null) {
      return head.sha;
    }
    return null;
  }

  @NotNull
  public Collection<String> getCommitParents(@NotNull String repoOwner, @NotNull String repoName, @NotNull String hash) throws IOException, PublisherException {

    final String requestUrl = myUrls.getCommitInfo(repoOwner, repoName, hash);

    final CommitInfo infos = processResponse(requestUrl, CommitInfo.class, false);
    if (infos.parents != null) {
      final Set<String> parents = new HashSet<String>();
      for (CommitInfo p : infos.parents) {
        String sha = p.sha;
        if (sha != null) {
          parents.add(sha);
        }
      }
      return parents;
    }
    return Collections.emptyList();
  }

  @NotNull
  private <T> T processResponse(@NotNull String uri, @NotNull final Class<T> clazz, boolean logErrorsDebugOnly) throws IOException, PublisherException {
    LoggerUtil.logRequest(Constants.GITHUB_PUBLISHER_ID, HttpMethod.GET, uri, null);

    final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    final AtomicReference<T> resultRef = new AtomicReference<>();
    IOGuard.allowNetworkCall(() -> {
      myClient.get(uri, authenticationCredentials(), defaultHeaders(),
                   success -> {
                     final String json = success.getBodyAsString();
                     if (StringUtil.isEmptyOrSpaces(json)) {
                       logFailedResponse(HttpMethod.GET, uri, null, success, logErrorsDebugOnly);
                       exceptionRef.set(new IOException(getErrorMessage(success, "Empty response.")));
                     } else {
                       LOG.debug("Parsing json for " + uri + ": " + json);
                       T result = myGson.fromJson(json, clazz);
                       if (null == result) {
                         exceptionRef.set(new PublisherException("GitHub publisher fails to parse a response"));
                       } else {
                         resultRef.set(result);
                       }
                     }
                   },
                   error -> {
                     logFailedResponse(HttpMethod.GET, uri, null, error, logErrorsDebugOnly);
                     String additionalComment = error.getStatusCode() == HttpStatus.SC_NOT_FOUND ? MSG_NOT_FOUND : MSG_PROXY_OR_PERMISSIONS;
                     exceptionRef.set(new IOException(getErrorMessage(error, additionalComment)));
                   },
                   e -> {
                     exceptionRef.set(e);
                   }
      );
    });

    final Exception ex;
    if ((ex = exceptionRef.get()) != null) {
      if (ex instanceof IOException) {
        throw (IOException)ex;
      } else if (ex instanceof PublisherException) {
        throw (PublisherException)ex;
      } else {
        throw new IOException(ex);
      }
    }

    return resultRef.get();
  }

  @NotNull
  private static String getErrorMessage(@NotNull HTTPRequestBuilder.Response response,
                                        @Nullable String additionalComment) {
    final BasicStatusLine statusLine =
      new BasicStatusLine(HTTP_1_1, response.getStatusCode(), response.getStatusText());
    String err = "";
    if (null != additionalComment) {
      err = additionalComment + " ";
    }
    return String.format("Failed to complete request to GitHub. %sStatus: %s", err, statusLine);
  }

  protected abstract SimpleCredentials authenticationCredentials() throws IOException;

  private void logFailedResponse(@NotNull HttpMethod method,
                                 @NotNull String uri,
                                 @Nullable String requestEntity,
                                 @NotNull HTTPRequestBuilder.Response response) throws IOException {
    logFailedResponse(method, uri, requestEntity, response, false);
  }


  private void logFailedResponse(@NotNull HttpMethod method,
                                 @NotNull String uri,
                                 @Nullable String requestEntity,
                                 @NotNull HTTPRequestBuilder.Response response,
                                 boolean debugOnly) throws IOException {
    String responseText = response.getBodyAsStringLimit(256 * 1024); //limit buffer with 256K
    if (responseText == null) {
      responseText = "<none>";
    }
    if (requestEntity == null) {
      requestEntity = "<none>";
    }

    final String logEntry = "Failed to complete query to GitHub with:\n" +
            "  requestURL: " + uri + "\n" +
            "  requestMethod: " + method + "\n" +
            "  requestEntity: " + requestEntity + "\n" +
            "  response: " + response.getStatusText() + "\n" +
            "  responseEntity: " + responseText;
    if (debugOnly) {
      LOG.debug(logEntry);
    } else {
      LOG.warn(logEntry);
    }
  }

  public void postComment(@NotNull final String ownerName,
                          @NotNull final String repoName,
                          @NotNull final String hash,
                          @NotNull final String comment) throws IOException {

    final String url = myUrls.getAddCommentUrl(ownerName, repoName, hash);
    final String entity = myGson.toJson(new IssueComment(comment));

    final HttpMethod method = HttpMethod.POST;
    LoggerUtil.logRequest(Constants.GITHUB_PUBLISHER_ID, method, url, entity);

    final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    IOGuard.allowNetworkCall(() -> {
      myClient.post(
        url, authenticationCredentials(), defaultHeaders(),
        entity, ContentType.APPLICATION_JSON.getMimeType(), ContentType.APPLICATION_JSON.getCharset(),
        response -> {
        },
        response -> {
          logFailedResponse(method, url, entity, response);
          exceptionRef.set(new IOException(getErrorMessage(response, null)));
        },
        e -> exceptionRef.set(e));
    });

    final Exception ex;
    if ((ex = exceptionRef.get()) != null) {
      if (ex instanceof IOException) {
        throw (IOException) ex;
      } else {
        throw new IOException(ex);
      }
    }
  }
}
