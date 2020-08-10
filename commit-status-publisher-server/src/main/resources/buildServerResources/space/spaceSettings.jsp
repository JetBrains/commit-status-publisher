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
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.Map"/>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.space.Constants"/>

<c:set var="isCredentialsConnection" value="${propertiesBean.properties[keys.spaceCredentialsType] == keys.spaceCredentialsConnection or empty propertiesBean.properties[keys.spaceCredentialsType]}"/>
<c:set var="isCredentialsUser" value="${propertiesBean.properties[keys.spaceCredentialsType] == keys.spaceCredentialsUser}"/>


<tr>
  <th><label>Connection Type:<l:star/></label></th>
  <td>
    <props:radioButtonProperty name="${keys.spaceCredentialsType}" id="${keys.spaceCredentialsConnection}" value="${keys.spaceCredentialsConnection}"
                               onclick="showCredentialsConnectionFields()" checked="${isCredentialsConnection}"/>
    <label for="${keys.spaceCredentialsConnection}">Use JetBrains Space connection</label>
    <br>
    <props:radioButtonProperty name="${keys.spaceCredentialsType}" id="${keys.spaceCredentialsUser}" value="${keys.spaceCredentialsUser}"
                               onclick="showCredentialsUserFields()" checked="${isCredentialsUser}"/>
    <label for="${keys.spaceCredentialsUser}">Use JetBrains Space service credentials</label>
  </td>
</tr>

<tr class="${keys.spaceCredentialsConnection}_row">
  <th><label for="prop:loginCheckbox">Connection:<l:star/></label></th>
  <td>
    <c:set var="loginProp" value="<%= Constants.SPACE_CONNECTION_ID %>"/>
    <c:set var="initialLoginVal" value="${propertiesBean.properties[keys.spaceConnectionId]}"/>
    <div style="float:left">
      <props:selectProperty name="${loginProp}">
        <props:option value="">No connection provided</props:option>
        <c:forEach var="conn" items="${oauthConnections.keySet()}">
          <props:option value="${conn.id}">
            <c:out value="${conn.connectionDisplayName}"/>
            <c:if test="${fn:startsWith(conn.parameters[keys.spaceServerUrl], 'http://')}"> (insecure)</c:if>
          </props:option>
        </c:forEach>
      </props:selectProperty>
      <c:set var="connectorType" value="<%=SpaceOAuthProvider.TYPE%>"/>
      <span class="smallNote">Add credentials via the
                <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=${connectorType}'/>" target="_blank">Project Connections</a> page</span>
      <props:hiddenProperty name="invalidConnection" value=""/>
      <span class="error" id="error_${loginProp}"></span>
    </div>
  </td>
</tr>

<tr class="${keys.spaceCredentialsUser}_row">
  <th><label for="${keys.spaceServerUrl}">JetBrains Space URL:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.spaceServerUrl}" className="longField"/>
    <span class="error" id="error_${keys.spaceServerUrl}"></span>
  </td>
</tr>

<tr class="${keys.spaceCredentialsUser}_row">
  <th><label for="${keys.spaceClientId}">Client ID:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.spaceClientId}" className="longField"/>
    <span class="error" id="error_${keys.spaceClientId}"></span>
  </td>
</tr>

<tr class="${keys.spaceCredentialsUser}_row">
  <th><label for="${keys.spaceClientSecret}">Client secret:<l:star/></label></th>
  <td>
    <props:passwordProperty name="${keys.spaceClientSecret}" className="longField"/>
    <span class="error" id="error_${keys.spaceClientSecret}"></span>
  </td>
</tr>

<tr>
  <th><label for="${keys.spaceProjectKey}">Space Project key:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.spaceProjectKey}" className="mediumField"/>
    <span class="smallNote">Project key from JetBrains Space</span>
    <span class="error" id="error_${keys.spaceProjectKey}"></span>
  </td>
</tr>

<tr>
  <th><label for="${keys.spaceCommitStatusPublisherDisplayName}">Commit status publisher display
    name:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.spaceCommitStatusPublisherDisplayName}" className="mediumField"/>
    <span class="smallNote">This name will be displayed for this service in Space UI</span>
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

<script>
  var updateVisibility = function () {
    BS.VisibilityHandlers.updateVisibility('${keys.spaceCredentialsConnection}_row');
    BS.VisibilityHandlers.updateVisibility('${keys.spaceCredentialsUser}_row');
  };

  var clearCredentialsConnectionFields = function () {
    $('${loginProp}').value = ''
  };

  var clearCredentialsUserFields = function () {
    $('${keys.spaceServerUrl}').value = '';
    $('${keys.spaceClientId}').value = '';
    $('${keys.spaceClientSecret}').value = '';
  };

  var showCredentialsConnectionFields = function () {
    $j('.${keys.spaceCredentialsConnection}_row').show();
    $j('.${keys.spaceCredentialsUser}_row').hide();
    clearCredentialsUserFields();
    updateVisibility();
  };

  var showCredentialsUserFields = function () {
    $j('.${keys.spaceCredentialsConnection}_row').hide();
    $j('.${keys.spaceCredentialsUser}_row').show();
    clearCredentialsConnectionFields();
    updateVisibility();
  };

  $j(document).ready(function() {
    if ('${isCredentialsConnection}' === 'true') {
      showCredentialsConnectionFields();
    }

    if ('${isCredentialsUser}' === 'true') {
      showCredentialsUserFields();
    }
  });
</script>

