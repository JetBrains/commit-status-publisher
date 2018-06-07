<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@include file="/include-internal.jsp"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<c:set var="cameFromUrl" value="${showMode eq inplaceMode ? pageUrl : healthStatusReportUrl}"/>
<c:set var="buildType" value="${healthStatusItem.additionalData['buildType']}"/>
<c:set var="rootUrl" value="${healthStatusItem.additionalData['rootUrl']}"/>
<c:set var="publisherType" value="${healthStatusItem.additionalData['publisherType']}"/>
<div>
    Commit Status Publisher in <admin:editBuildTypeLinkFull buildType="${buildType}" cameFromUrl="${cameFromUrl}"/>
    may not work correctly with <c:out value="${publisherType}" /> due to TeamCity Server URL (<strong><c:out value="${rootUrl}" /></strong>)
  referring to a not fully qualified domain name. Please contact your system administrator.
</div>