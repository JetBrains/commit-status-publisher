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
<c:set var="missingVcsRoot" value="${healthStatusItem.additionalData['missingVcsRoot']}"/>
<c:set var="featureId" value="${healthStatusItem.additionalData['featureId']}"/>
<div>
    Commit Status Publisher in <admin:editBuildTypeLinkFull buildType="${buildType}" cameFromUrl="${cameFromUrl}"/>
    refers to
    <c:choose>
        <c:when test="${not empty missingVcsRoot}">
            the VCS root
            <admin:editVcsRootLink vcsRoot="${missingVcsRoot}" editingScope="" cameFromUrl="${cameFromUrl}">
                <c:out value="${missingVcsRoot.name}" />
            </admin:editVcsRootLink>
        </c:when>
        <c:otherwise>
            a VCS root
        </c:otherwise>
    </c:choose>
    that is not attached to the build configuration.
    <c:if test="${not buildType.readOnly}">
        <div>
            <c:url var="url" value="/admin/editBuildFeatures.html?init=1&id=buildType:${buildType.externalId}#editFeature=${featureId}" />
            <a href="${url}">Edit</a>
        </div>
    </c:if>
</div>