<%@ page import="jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="oauth" tagdir="/WEB-INF/tags/oauth" %>


<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/>

<%--@elvariable id="canEditProject" type="java.lang.Boolean"--%>

<props:selectSectionProperty name="${keys.authType}" title="Authentication Type" style="width: 28em;">

  <props:selectSectionPropertyContent value="${keys.authTypePassword}" caption="Username / Password">
    <tr>
      <th><label for="${keys.bitbucketCloudUsername}">Bitbucket Username:<l:star/></label></th>
      <td>
        <props:textProperty name="${keys.bitbucketCloudUsername}" className="longField"/>
        <span class="error" id="error_${keys.bitbucketCloudUsername}"></span>
      </td>
    </tr>

    <tr>
      <th><label for="${keys.bitbucketCloudPassword}">Bitbucket App Password:<l:star/></label></th>
      <td>
        <props:passwordProperty name="${keys.bitbucketCloudPassword}" className="longField"/>
        <span class="error" id="error_${keys.bitbucketCloudPassword}"></span>
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
          <span>There are no Bitbucket connections available to the project.</span>
        </c:if>

        <c:set var="canObtainTokens" value="${canEditProject and not project.readOnly}"/>
        <c:set var="connectorType" value="<%=BitBucketOAuthProvider.TYPE%>"/>
        <oauth:tokenControlsForFeatures
          project="${project}"
          providerTypes="'${connectorType}'"
          tokenIntent="PUBLISH_STATUS"
          canObtainTokens="${canObtainTokens}"
          callback="BS.AuthTypeTokenSupport.tokenCallback"
          oauthConnections="${oauthConnections}">
          <jsp:attribute name="addCredentialFragment">
            <span class="smallNote connection-note">Add credentials via the
                  <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=${connectorType}'/>" target="_blank" rel="noreferrer">Project Connections</a> page</span>
          </jsp:attribute>
        </oauth:tokenControlsForFeatures>

        <props:hiddenProperty name="${keys.tokenId}" />
        <span class="error" id="error_${keys.tokenId}"></span>
      </td>
    </tr>
  </props:selectSectionPropertyContent>

  <props:selectSectionPropertyContent value="${keys.authTypeVCS}" caption="Use VCS root(-s) credentials">
    <tr><td colspan="2">
      <em>
        TeamCity obtains App password / token based credentials from the VCS root settings.
        This option will not work if the VCS root uses an SSH fetch URL,
        employs anonymous authentication or uses
        an actual password
        of the user rather than a token.
      </em>
    </td></tr>
  </props:selectSectionPropertyContent>

  <c:if test="${testConnectionSupported}">
    <script>
      $j(document).ready(function() {
        PublisherFeature.showTestConnection("This ensures that the repository is reachable under the provided credentials.\nIf status publishing still fails, it can be due to insufficient permissions of the corresponding BitBucket Cloud user.");
      });
    </script>
  </c:if>

</props:selectSectionProperty>