<%@ page import="jetbrains.buildServer.web.openapi.PlaceId" %>
<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>

<%--
  ~ Copyright 2000-2021 JetBrains s.r.o.
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
<jsp:useBean id="oauthConnections" scope="request" type="java.util.Map"/>

<c:url value="/oauth/tfs/token.html" var="getTokenPage"/>
<c:set var="cameFromUrl" value="${empty param['cameFromUrl'] ? pageUrl : param['cameFromUrl']}"/>
<c:set var="getTokenPage" value="${getTokenPage}?cameFromUrl=${util:urlEscape(cameFromUrl)}"/>

<c:set var="oauth_connection_fragment">
  <c:forEach items="${oauthConnections.keySet()}" var="connection">
    <c:set var="title">Acquire an access token from <c:out value="${connection.parameters['serverUrl']}"/></c:set>
    <span class="tfsRepoControl">
      <i class="icon-magic" style="cursor:pointer;" title="${title}"
         onclick="BS.TfsAccessTokenPopup.showPopup(this, '${connection.id}')"></i>
    </span>
  </c:forEach>
</c:set>

<tr>
  <th><label for="${keys.accessTokenKey}">Access Token:<l:star/></label></th>
  <td>
    <props:passwordProperty name="${keys.accessTokenKey}" className="mediumField"/>
    ${oauth_connection_fragment}
    <props:hiddenProperty name="${keys.authenticationTypeKey}"/>
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
      for token and add the "+:refs/(pull/*)/merge" rule in VCS root branch specification.
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

