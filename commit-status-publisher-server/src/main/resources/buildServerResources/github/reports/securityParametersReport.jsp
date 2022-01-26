<%@include file="/include-internal.jsp"%>
<%@ page import="jetbrains.buildServer.controllers.admin.projects.BuildConfigurationSteps" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
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

<div>
    Build configuration has
    <authz:authorize allPermissions="EDIT_PROJECT" projectId="${buildType.projectId}">
        <jsp:attribute name="ifAccessGranted">
            <admin:editBuildTypeLink buildTypeId="${buildType.externalId}" step="<%=BuildConfigurationSteps.PARAMETERS_STEP_ID%>">secure parameters</admin:editBuildTypeLink>
        </jsp:attribute>
        <jsp:attribute name="ifAccessDenied">
            secure parameters
        </jsp:attribute>
    </authz:authorize>
    and builds pull requests. <b>Pull request from any user can access secure parameters.</b>

    VCS root<bs:s val="${fn:length(roots)}"/> with pull requests<c:if test="${fn:length(roots) > 1}">:</c:if>
    <c:choose>
        <c:when test="${fn:length(roots) > 1}">
            <c:forEach var="root" items="${roots}">
                <div>
                    <admin:vcsRootName vcsRoot="${root.parent}" editingScope="none" cameFromUrl="${healthStatusReportUrl}"/>
                </div>
            </c:forEach>
        </c:when>
        <c:otherwise>
            <c:forEach var="root" items="${roots}">
                <admin:vcsRootName vcsRoot="${root.parent}" editingScope="none" cameFromUrl="${healthStatusReportUrl}"/>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>