<%--
  ~ Copyright 2000-2024 JetBrains s.r.o.
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

<%@ page import="jetbrains.buildServer.swarm.SwarmConstants" %>
<%@include file="/include.jsp"%>


<%--@elvariable id="swarmBean" type="jetbrains.buildServer.swarm.web.SwarmBuildDataBean"--%>

<bs:executeOnce id="swarmStyles">
  <script>
    <c:set var="pluginName" value="<%= SwarmConstants.PLUGIN_NAME%>"/>
    BS.LoadStyleSheetDynamically("<c:url value='/plugins/${pluginName}/swarm/swarm.css'/>")
  </script>
</bs:executeOnce>

  <bs:refreshable containerId="pullRequestFullInfo" pageUrl="${pageUrl}">
  <bs:_collapsibleBlock title="Swarm Reviews" id="smarmReviews" contentClass="swarmReviews">

    <c:if test="${not swarmBean.reviewsPresent}">
      No reviews found.
    </c:if>
    <c:if test="${swarmBean.reviewsPresent}">
    <ul class="swarmReviewsList">
      <c:forEach items="${swarmBean.reviews}" var="serverData">
        <li>
          <span class="swarmReviewsList__changelist">
              Changelist:

              <c:if test="${serverData.shelved}">
                <c:set var="imageName" value="${'p4v-icon-pending-changelist-with-swarm_15x15.webp'}"/>
                <img src="<c:url value="/plugins/${pluginName}/swarm/${imageName}"/>"
                     class="swarmReviewsList__typeIcon" alt="Shelved changelist with Swarm review" title="Shelved changelist with Swarm review"/>
              </c:if>
              <c:if test="${not serverData.shelved}">
                <c:set var="imageName" value="${'p4v-icon-submitted-changelist-with-swarm_15x15.webp'}"/>
                <img src="<c:url value="/plugins/${pluginName}/swarm/${imageName}"/>"
                     class="swarmReviewsList__typeIcon" alt="Submitted changelist with Swarm review" title="Submitted changelist with Swarm review"/>
              </c:if>

              <c:set var="url"><c:out value="${serverData.url}"/>/changes/${serverData.changelist}</c:set>
              <a href="${url}" target="_blank" rel="noopener" title="Open Helix Swarm page for the changelist">${serverData.changelist}</a>

          </span>

          <span class="swarmReviewsList__reviews">
              Review<bs:s val="${fn:length(serverData.reviews)}"/>:

              <c:forEach items="${serverData.reviews}" var="review">
                <c:set var="url"><c:out value="${serverData.url}"/>/reviews/${review.id}</c:set>
                <a href="${url}" target="_blank" rel="noopener" title="Open Helix Swarm page for the review">${review.id}</a>
                (${review.statusText})
              </c:forEach>
          </span>
          
        </li>
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
      onComplete() {
        endProgress();
        $('pullRequestFullInfo').refresh();
      }
    });
  }
  console.info("Run Perforce Swarm page extension, empty: ${swarmBean.reviewsPresent}");
</script>