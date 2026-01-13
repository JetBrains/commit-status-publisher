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


<%--@elvariable id="swarmChangeUrl" type="java.lang.String"--%>
<%--@elvariable id="showType" type="java.lang.String"--%>

<c:if test="${not empty swarmChangeUrl}">

  <bs:executeOnce id="swarmStyles">
    <script>
      <c:set var="pluginName" value="<%= SwarmConstants.PLUGIN_NAME%>"/>
      BS.LoadStyleSheetDynamically("<c:url value='/plugins/${pluginName}/swarm/swarm.css'/>")
    </script>
  </bs:executeOnce>

  <c:set var="title" value="Open in P4 Code Review"/>
  <c:set var="icon" value="swarmIconClass"/>
  <c:choose>
    <c:when test="${showType == 'compact'}">
      <a href="${swarmChangeUrl}" title="${title}" class="noUnderline" target="_blank" rel="noreferrer"><i class="icon16 ${icon}"></i></a>
    </c:when>
    <c:otherwise>
      <dt>
        <span class="icon16 ${icon}"></span>
        <a href="${swarmChangeUrl}" target="_blank" rel="noreferrer">${title}</a>
      </dt>
    </c:otherwise>
  </c:choose>
</c:if>