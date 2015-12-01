<%@include file="/include-internal.jsp"%>
<%@ page import="jetbrains.buildServer.controllers.admin.projects.BuildConfigurationSteps" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<div>
    Build configuration
    <authz:authorize allPermissions="EDIT_PROJECT" projectId="${buildType.projectId}">
        <jsp:attribute name="ifAccessGranted">
            <admin:editBuildTypeLink buildTypeId="${buildType.externalId}" step="<%=BuildConfigurationSteps.PARAMETERS_STEP_ID%>"><c:out value="${buildType.fullName}"/></admin:editBuildTypeLink>
        </jsp:attribute>
        <jsp:attribute name="ifAccessDenied">
            <c:out value="${buildType.fullName}"/>
        </jsp:attribute>
    </authz:authorize>
    has secure parameters which will be accessible in pull request builds from VCS root<bs:s val="${fn:length(roots)}"/><c:if test="${fn:length(roots) > 1}">:</c:if>
    <c:choose>
        <c:when test="${fn:length(roots) > 1}">
            <c:forEach var="root" items="${roots}">
                <div>
                    <admin:vcsRootName vcsRoot="${root.parent}" editingScope="none" cameFromUrl="${pageUrl}"/>
                </div>
            </c:forEach>
        </c:when>
        <c:otherwise>
            <c:forEach var="root" items="${roots}">
                <admin:vcsRootName vcsRoot="${root.parent}" editingScope="none" cameFromUrl="${pageUrl}"/>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>