<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%--
  ~ Copyright 2000-2022 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%--@elvariable id="swarmBean" type="jetbrains.buildServer.swarm.web.SwarmBuildDataBean"--%>

<style>
  .swarmReviews {
    margin: 0;
  }
  .swarmReviewsAge {
    margin-top: 10px;
  }
  .swarmReviewsAge--progress .swarmReviewsAge__refresh {
    display: none;
  }
  .swarmReviewsAge__error {
    color: var(--ring-error-color);
    display: block;
  }
</style>

  <bs:refreshable containerId="pullRequestFullInfo" pageUrl="${pageUrl}">
  <bs:_collapsibleBlock title="Swarm Reviews" id="smarmReviews" contentClass="swarmReviews">

    Perforce changelist(s):
    <c:forEach items="${swarmBean.swarmClientRevisions}" var="change">
        <c:set var="url"><c:out value="${change.first.swarmServerUrl}"/>/changes/${change.second}</c:set>
        <a href="${url}" target="_blank" rel="noopener" title="Open Helix Swarm page for the changelist">${change.second}</a>
    </c:forEach>

    <c:if test="${not swarmBean.reviewsPresent}">
      No reviews found.
    </c:if>
    <c:if test="${swarmBean.reviewsPresent}">
    <ul>
      <c:forEach items="${swarmBean.reviews}" var="serverData">
        <c:forEach items="${serverData.reviews}" var="review">
          <c:set var="url"><c:out value="${serverData.url}"/>/reviews/${review.id}</c:set>
          <li>Review <a href="${url}" target="_blank" rel="noopener" title="Open Helix Swarm page for the review">${review.id}</a>
          (${review.statusText})</li>
        </c:forEach>
      </c:forEach>
    </ul>
    </c:if>

    <div class="swarmReviewsAge">
      <c:if test="${swarmBean.hasData}">
        The data were retrieved <bs:printTime time="${swarmBean.retrievedAge.seconds}"/> ago.
      </c:if>
      <c:if test="${not swarmBean.hasData}">
        No data yet.
      </c:if>
      <span class="swarmReviewsAge__refresh">
        <bs:actionIcon
          name="update"
          onclick="refreshSwarmInfo('${buildData.buildId}'); return false;"
          title="Refresh Helix Swarm information"
        />
      </span>
      <span class="swarmReviewsAge__progressIcon"></span>
      <span class="swarmReviewsAge__error"><c:out value="${not empty swarmBean.error ? swarmBean.error.message : ''}"/></span>
    </div>

  </bs:_collapsibleBlock>
  </bs:refreshable>

  <c:if test="${not swarmBean.hasData or (swarmBean.retrievedAge.toMillis() > 60 * 1000)}">
    <script>
      $j(document).ready(function() {
        refreshSwarmInfo('${buildData.buildId}');
      });
    </script>
  </c:if>

<script>
  function refreshSwarmInfo(buildId) {
    console.info("Re-read Perforce Swarm info");
    const startProgress = () => {
      document.querySelector(".swarmReviewsAge").classList.add("swarmReviewsAge--progress");
      document.querySelector(".swarmReviewsAge__error").innerHTML = '';
      document.querySelector(".swarmReviewsAge__progressIcon").innerHTML = '<i class="icon-refresh icon-spin ring-loader-inline" />';
    };
    const endProgress = () => {
      document.querySelector(".swarmReviewsAge").classList.remove("swarmReviewsAge--progress");
      document.querySelector(".swarmReviewsAge__progressIcon").innerHTML = '';
    };

    startProgress();
    BS.ajaxRequest(window["base_uri"] + "/app/commit-status-publisher/swarm/loadReviews?buildId=" + parseInt(buildId), {
      onSuccess() {
        $('pullRequestFullInfo').refresh();
      },
      onFailure(response) {
        document.querySelector(".swarmReviewsAge__error").innerHTML = "Error: " + response.responseText.stripTags();
      },
      onComplete() {
        endProgress();
      }
    });
  }
  console.info("Run Perforce Swarm page extension, empty: ${swarmBean.reviewsPresent}");
</script>



