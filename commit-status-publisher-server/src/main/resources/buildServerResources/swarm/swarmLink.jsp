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

  <c:set var="title" value="Open in Helix Swarm"/>
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