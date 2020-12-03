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
<c:set var="message" value="${healthStatusItem.additionalData['message']}"/>
<c:set var="featureId" value="${healthStatusItem.additionalData['featureId']}"/>
<div>
    Commit Status Publisher in <admin:editBuildTypeLinkFull buildType="${buildType}" cameFromUrl="${cameFromUrl}"/> <c:out value="${message}" />
    <c:if test="${not buildType.readOnly}">
        <div>
            <c:url var="url" value="/admin/editBuildFeatures.html?init=1&id=buildType:${buildType.externalId}#editFeature=${featureId}" />
            <a href="${url}">Edit</a>
        </div>
    </c:if>
</div>