<%@ page import="jetbrains.buildServer.commitPublisher.space.Constants" %>
<%@ page import="jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider" %>
<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop"%>
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

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List"/>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.space.Constants"/>
<jsp:useBean id="spaceFeatures" scope="request" type="jetbrains.buildServer.serverSide.oauth.space.SpaceFeatures"/>
<jsp:useBean id="rightsInfo" scope="request" type="java.lang.String"/>

<c:set var="capabilitiesEnabled" value='${spaceFeatures.capabilitiesEnabled()}'/>
<c:set var="idSelectedConnection" value="selectedConnection"/>
<c:set var="idConnectionsWaiter" value="connectionsWaiter"/>
<c:set var="idNonMatchingConnectionsHint" value="nonMatchingConnectionsHint"/>

<tr class="${keys.spaceCredentialsConnection}_row">
  <th><label for="prop:loginCheckbox">Connection:<l:star/></label></th>
  <td>
    <props:hiddenProperty name="${keys.spaceCredentialsType}" value="${keys.spaceCredentialsConnection}" />
    <c:set var="loginProp" value="<%= Constants.SPACE_CONNECTION_ID %>"/>
    <c:set var="initialLoginVal" value="${propertiesBean.properties[keys.spaceConnectionId]}"/>
    <div style="float:left">
      <props:selectProperty name="${loginProp}" style="width: 28em;">
        <c:if test="${not capabilitiesEnabled}">
          <c:forEach var="conn" items="${oauthConnections}">
            <props:option value="">No connection provided</props:option>
            <props:option value="${conn.id}">
              <c:out value="${conn.connectionDisplayName}"/>
              <c:if test="${fn:startsWith(conn.parameters[keys.spaceServerUrl], 'http://')}"> (insecure)</c:if>
            </props:option>
          </c:forEach>
        </c:if>
      </props:selectProperty>
      <c:if test="${capabilitiesEnabled}">
        <props:hiddenProperty id="${idSelectedConnection}" name="${loginProp}"/>
        <forms:progressRing id="${idConnectionsWaiter}" progressTitle="Loading Space connections..."/>
      </c:if>
      <span id="${idNonMatchingConnectionsHint}" style="display: none;">
        <bs:helpIcon iconTitle="The commit status publisher feature requires the following Space application permissions: ${rightsInfo}. Connections that utilize applications without these permissions are not available."/>
      </span>
      <%--@elvariable id="canEditProject" type="java.lang.Boolean"--%>
      <c:if test="${canEditProject}">
        <c:set var="connectorType" value="<%=SpaceOAuthProvider.TYPE%>"/>
        <span class="smallNote">Add credentials via the
                <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=${connectorType}'/>" target="_blank" rel="noreferrer">Project Connections</a> page</span>
        <props:hiddenProperty name="invalidConnection" value=""/>
      </c:if>
      <span class="error" id="error_${loginProp}"></span>
    </div>
  </td>
</tr>

<c:if test="${intprop:getBoolean('teamcity.commitStatusPublisher.space.ui.projectKey.enabled')}">
<tr class="advancedSetting">
  <th><label for="${keys.spaceProjectKey}">Space Project key:</label></th>
  <td>
    <props:textProperty name="${keys.spaceProjectKey}" className="longField"/>
    <span class="smallNote">If not provided the project key will be extracted from the fetch URL of the respective VCS root</span>
    <span class="smallNote">Project key from JetBrains Space</span>
    <span class="error" id="error_${keys.spaceProjectKey}"></span>
  </td>
</tr>
</c:if>

<tr class="advancedSetting">
  <th><label for="${keys.spaceCommitStatusPublisherDisplayName}">Display
    name:</label></th>
  <td>
    <props:textProperty name="${keys.spaceCommitStatusPublisherDisplayName}" className="longField"/>
    <span class="smallNote">If provided this name will be displayed for this service in Space UI. The default display name is "TeamCity".</span>
    <span class="error" id="error_${keys.spaceCommitStatusPublisherDisplayName}"></span>
    <c:if test="${testConnectionSupported}">
      <script>
        $j(document).ready(function () {
          PublisherFeature.showTestConnection();
        });
      </script>
    </c:if>
  </td>
</tr>

<jsp:include page="/oauth/space/spaceConnectionsService.jsp"/>
<script type="text/javascript">
  BS.SpaceCspSettings = {
    fetchConnections: function () {
      BS.SpaceConnectionsService.fetchConnectionsWithCapabilitiesIntoSelect({
        waiterElement: $('${idConnectionsWaiter}'),
        selectElement: $j('#${loginProp}'),
        selectedElement: $('${idSelectedConnection}'),
        projectId: '${project.projectId}',
        capabilities: ['PUBLISH_BUILD_STATUS'],
        fullMatch: true,
        addSpecialOptions: function (select) {
          select.append(new Option("No connection provided", ""));
        },
        buildDescription: function (connection) {
          let description = connection.displayName;
          if (connection.serverUrl && connection.serverUrl.startsWith('http://')) {
            description += ' (insecure)';
          }
          return description;
        },
        onNonMatchingConnections: function (connections) {
          $('${idNonMatchingConnectionsHint}').show();
        }
      });
    }
  };

  <c:if test="${capabilitiesEnabled}">
    BS.SpaceCspSettings.fetchConnections();
  </c:if>
</script>

<script>
  var updateVisibility = function () {
    BS.VisibilityHandlers.updateVisibility('${keys.spaceCredentialsConnection}_row');
  };

  var clearCredentialsConnectionFields = function () {
    $('${loginProp}').value = ''
  };

  var showCredentialsConnectionFields = function () {
    $j('.${keys.spaceCredentialsConnection}_row').show();
    updateVisibility();
  };

  $j(document).ready(function() {
    if ('${isCredentialsConnection}' === 'true') {
      showCredentialsConnectionFields();
    }
  });
</script>

