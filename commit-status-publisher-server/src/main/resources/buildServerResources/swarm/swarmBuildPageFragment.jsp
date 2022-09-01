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

<jsp:useBean id="swarmBean" class="jetbrains.buildServer.swarm.web.SwarmBuildDataBean" scope="request"/>

<style>
  .swarmReviews {
    margin: 0;
  }
</style>

<c:if test="${swarmBean.dataPresent}">

  <bs:_collapsibleBlock title="Open Swarm Reviews" id="smarmReviews" contentClass="swarmReviews">
    <ul>
      <c:forEach items="${swarmBean.reviews}" var="serverData">
        <c:forEach items="${serverData.reviewIds}" var="reviewId">
          <c:set var="url"><c:out value="${serverData.url}"/>/reviews/${reviewId}</c:set>
          <li><span class="grayNote">Reviews</span> / <a href="${url}" target="_blank" rel="noopener">${reviewId}</a></li>
        </c:forEach>
      </c:forEach>
    </ul>
  </bs:_collapsibleBlock>

</c:if>

<script>
  console.info("Run Perforce Swarm page extension, empty: ${swarmBean.dataPresent}");
</script>



