<%@ page import="jetbrains.buildServer.commitPublisher.space.SpaceConstants" %>
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


<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List"/>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.space.SpaceConstants"/>
<jsp:useBean id="spaceFeatures" scope="request" type="jetbrains.buildServer.serverSide.oauth.space.SpaceFeatures"/>
<jsp:useBean id="rightsInfo" scope="request" type="java.lang.String"/>

<c:set var="capabilitiesEnabled" value='${spaceFeatures.capabilitiesEnabled()}'/>
<c:set var="idSelectedConnection" value="selectedConnection"/>
<c:set var="idConnectionsWaiter" value="connectionsWaiter"/>
<c:set var="idNonMatchingConnectionsHint" value="nonMatchingConnectionsHint"/>

<tr class="${keys.credentialsConnection}_row">
  <th><label for="prop:loginCheckbox">Connection:<l:star/></label></th>
  <td>
    <props:hiddenProperty name="${keys.credentialsType}" value="${keys.credentialsConnection}" />
    <c:set var="loginProp" value="<%= SpaceConstants.CONNECTION_ID %>"/>
    <c:set var="initialLoginVal" value="${propertiesBean.properties[keys.connectionId]}"/>
    <div style="float:left">
      <props:selectProperty name="${loginProp}" style="width: 28em;">
        <c:if test="${not capabilitiesEnabled}">
          <c:forEach var="conn" items="${oauthConnections}">
            <props:option value="">No connection provided</props:option>
            <props:option value="${conn.id}">
              <c:out value="${conn.connectionDisplayName}"/>
              <c:if test="${fn:startsWith(conn.parameters[keys.serverUrl], 'http://')}"> (insecure)</c:if>
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
  <th><label for="${keys.projectKey}">Space Project key:</label></th>
  <td>
    <props:textProperty name="${keys.projectKey}" className="longField"/>
    <span class="smallNote">If not provided the project key will be extracted from the fetch URL of the respective VCS root</span>
    <span class="smallNote">Project key from JetBrains Space</span>
    <span class="error" id="error_${keys.projectKey}"></span>
  </td>
</tr>
</c:if>

<tr class="advancedSetting">
  <th><label for="${keys.commitStatusPublisherDisplayName}">Display
    name:</label></th>
  <td>
    <props:textProperty name="${keys.commitStatusPublisherDisplayName}" className="longField"/>
    <span class="smallNote">If provided this name will be displayed for this service in Space UI. The default display name is "TeamCity".</span>
    <span class="error" id="error_${keys.commitStatusPublisherDisplayName}"></span>
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
    BS.VisibilityHandlers.updateVisibility('${keys.credentialsConnection}_row');
  };

  var clearCredentialsConnectionFields = function () {
    $('${loginProp}').value = ''
  };

  var showCredentialsConnectionFields = function () {
    $j('.${keys.credentialsConnection}_row').show();
    updateVisibility();
  };

  $j(document).ready(function() {
    if ('${isCredentialsConnection}' === 'true') {
      showCredentialsConnectionFields();
    }
  });
</script>