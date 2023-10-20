<%@ page import="jetbrains.buildServer.serverSide.oauth.azuredevops.AzureDevOpsOAuthProvider" %>
<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="oauth" tagdir="/WEB-INF/tags/oauth" %>

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

<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.tfs.TfsConstants"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List"/>
<jsp:useBean id="azurePatConnections" scope="request" type="java.util.List"/>

<%--@elvariable id="canEditProject" type="java.lang.Boolean"--%>

<c:url value="/oauth/tfs/token.html" var="getTokenPage"/>
<c:set var="cameFromUrl" value="${empty param['cameFromUrl'] ? pageUrl : param['cameFromUrl']}"/>
<c:set var="getTokenPage" value="${getTokenPage}?cameFromUrl=${util:urlEscape(cameFromUrl)}"/>

<c:set var="oauth_connection_fragment">
  <c:forEach items="${azurePatConnections}" var="PATconnection">
    <c:set var="title">Acquire an access token from <c:out value="${PATconnection.parameters['serverUrl']}"/></c:set>
    <span class="tfsRepoControl">
      <i class="icon-magic" style="cursor:pointer;" title="${title}"
         onclick="BS.TfsAccessTokenPopup.showPopup(this, '${PATconnection.id}')"></i>
    </span>
  </c:forEach>
</c:set>

<props:selectSectionProperty name="${keys.authenticationTypeKey}" title="Authentication Type">
  <props:selectSectionPropertyContent value="${keys.authTypeToken}" caption="Personal Token">
    <tr>
      <th><label for="${keys.accessTokenKey}">Access Token:<l:star/></label></th>
      <td>
        <props:passwordProperty name="${keys.accessTokenKey}" className="mediumField"/>
          ${oauth_connection_fragment}
        <props:hiddenProperty name="${keys.authUser}"/>
        <props:hiddenProperty name="${keys.authProviderId}"/>
        <span class="error" id="error_${keys.accessTokenKey}"></span>
        <span class="smallNote">
      This publisher supports build status update only for Git repositories.<br/>
      You need to grant <strong><em>Code (status)</em></strong> and <strong><em>Code (read)</em></strong>
      <a href="https://docs.microsoft.com/en-us/azure/devops/integrate/get-started/authentication/oauth?view=vsts#scopes" target="_blank" rel="noreferrer">scopes</a>
      for token.
    </span>
      </td>
    </tr>
  </props:selectSectionPropertyContent>
  <props:selectSectionPropertyContent value="${keys.authTypeStoredToken}" caption="Refreshable access token">
    <%@include file="/admin/_tokenSupport.jspf"%>
    <tr>
      <th><label for="${keys.tokenId}">Refreshable access token:<l:star/></label></th>
      <td>
        <span class="access-token-note" id="message_no_token">No access token configured.</span>
        <span class="access-token-note" id="message_we_have_token"></span>
        <c:if test="${empty oauthConnections}">
          <br/>
          <span>There are no Azure DevOps OAuth 2.0 connections available to the project.</span>
        </c:if>

        <props:hiddenProperty name="${keys.tokenId}" />
        <span class="error" id="error_${keys.tokenId}"></span>

        <c:if test="${canEditProject and not project.readOnly}">
          <c:forEach items="${oauthConnections}" var="connection">
            <script type="application/javascript">
              BS.AuthTypeTokenSupport.connections['${connection.id}'] = '<bs:forJs>${connection.connectionDisplayName}</bs:forJs>';
            </script>
            <div class="token-connection">
              <span class="token-connection-diplay-name"><c:out value="${connection.connectionDisplayName}" /></span>
              <oauth:obtainToken connection="${connection}" className="btn btn_small token-connection-button" callback="BS.AuthTypeTokenSupport.tokenCallback">
                Acquire
              </oauth:obtainToken>
            </div>
          </c:forEach>

          <c:set var="connectorType" value="<%=AzureDevOpsOAuthProvider.TYPE%>"/>
          <span class="smallNote connection-note">Add credentials via the
                  <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=${connectorType}'/>" target="_blank" rel="noreferrer">Project Connections</a> page</span>
        </c:if>
      </td>
    </tr>
  </props:selectSectionPropertyContent>

</props:selectSectionProperty>

<tr class="advancedSetting">
  <th><label for="${keys.serverUrl}">Server URL:</label></th>
  <td>
    <props:textProperty name="${keys.serverUrl}" className="longField"/>
    <span class="error" id="error_${keys.serverUrl}"></span>
    <span class="smallNote">
      Server URL for SSH-based VCS roots: http[s]://&lt;host&gt;[:&lt;port&gt;]
    </span>
  </td>
</tr>

<tr class="advancedSetting">
  <th>Options:</th>
  <td>
    <props:checkboxProperty name="${keys.publishPullRequests}"/>
    <label for="${keys.publishPullRequests}">Publish pull request statuses</label>
    <span class="smallNote">
      You need to grant <strong><em>Code (status)</em></strong> and <strong><em>Code (write)</em></strong>
      <a href="https://docs.microsoft.com/en-us/azure/devops/integrate/get-started/authentication/oauth?view=vsts#scopes" target="_blank" rel="noreferrer">scopes</a>
      for token and add <a href="https://www.jetbrains.com/help/teamcity/pull-requests.html" target="_blank">Pull Requests build feature</a>.
    </span>
  </td>
</tr>

<c:if test="${testConnectionSupported}">
  <script>
    $j(document).ready(function () {
      PublisherFeature.showTestConnection();
    });
  </script>
</c:if>

<script type="text/javascript">

  BS.TfsAccessTokenPopup = new BS.Popup('tfsGetToken', {
    url: "${getTokenPage}",
    method: "get",
    hideDelay: 0,
    hideOnMouseOut: false,
    hideOnMouseClickOutside: true
  });

  BS.TfsAccessTokenPopup.showPopup = function (nearestElement, connectionId) {
    this.options.parameters = "projectId=${project.externalId}&connectionId=" + connectionId + "&showMode=popup";
    var that = this;

    window.TfsTokenContentUpdater = function () {
      that.hidePopup(0);
      that.showPopupNearElement(nearestElement);
    };
    this.showPopupNearElement(nearestElement);
  };

  window.getOAuthTokenCallback = function (cre) {
    if (cre != null) {
      $('${keys.authUser}').value = cre.oauthLogin;
      $('${keys.authProviderId}').value = cre.oauthProviderId;
      if (cre.serverUrl && !/(visualstudio\.com|dev\.azure\.com)/.test(cre.serverUrl)) {
        $('${keys.serverUrl}').value = cre.serverUrl;
      }
      $('${keys.accessTokenKey}').value = '******************************'
    }
    BS.TfsAccessTokenPopup.hidePopup(0, true);
  };
</script>

<style type="text/css">
  .tc-icon_tfs {
    cursor: pointer;
  }

  a > .tc-icon_tfs_disabled {
    text-decoration: none;
  }
</style>

