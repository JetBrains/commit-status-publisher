<%@ page import="jetbrains.buildServer.web.openapi.PlaceId" %>
<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>

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
      <a href="https://www.visualstudio.com/en-us/docs/integrate/get-started/auth/oauth#scopes" target="_blank">scopes</a>
      for token.
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
      <a href="https://www.visualstudio.com/en-us/docs/integrate/get-started/auth/oauth#scopes" target="_blank">scopes</a>
      for token.
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

