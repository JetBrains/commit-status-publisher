<%--
  ~ Copyright 2000-2024 JetBrains s.r.o.
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

<%@ page import="jetbrains.buildServer.serverSide.TeamCityProperties" %>
<%@ page import="static jetbrains.buildServer.commitPublisher.Constants.BUILD_NAME_CUSTOMIZATION_TOGGLE_ENABLE" %>
<c:set var="customBuildNameEnable" value="<%= TeamCityProperties.getBoolean(BUILD_NAME_CUSTOMIZATION_TOGGLE_ENABLE) %>"/>

<%--@elvariable id="canEditProject" type="java.lang.Boolean"--%>
<%--@elvariable id="defaultBuildName" type="java.lang.String"--%>

<c:if test="${customBuildNameEnable}">
  <tr>
    <th><label for="${keys.buildName}">Build name (status context):</label></th>
    <td>
      <props:textProperty name="${keys.buildName}" className="longField"/>
      <span class="error" id="error_${keys.buildName}"></span>
      <span class="smallNote">
          Specifies the name of the build displayed in the status message posted to the Bitbucket Cloud
      </span>
      <script type="text/javascript">
        $j(document).ready(function() {
          if("${not empty defaultBuildName}" === "true") {
            document.getElementById('${keys.buildName}').setAttribute('placeholder', '${defaultBuildName}');
          }
        });
      </script>
    </td>
  </tr>
</c:if>

<props:selectSectionProperty name="${keys.authType}" title="Authentication Type" style="width: 28em;">

  <props:selectSectionPropertyContent value="${keys.authTypePassword}" caption="Username / Password">
    <tr>
      <th><label for="${keys.bitbucketCloudUsername}">Bitbucket Username:<l:star/></label></th>
      <td>
        <props:textProperty name="${keys.bitbucketCloudUsername}" className="longField"/>
        <span class="smallNote">For <a href="https://support.atlassian.com/bitbucket-cloud/docs/api-tokens/" target="_blank" rel="noopener noreferrer">API tokens</a>, use email as username.</span>
        <span class="error" id="error_${keys.bitbucketCloudUsername}"></span>
      </td>
    </tr>

    <tr>
      <th><label for="${keys.bitbucketCloudPassword}">Bitbucket Password:<l:star/></label></th>
      <td>
        <props:passwordProperty name="${keys.bitbucketCloudPassword}" className="longField"/>
        <span class="smallNote">API token or App Password with the <b><i>Repository &gt; Read</i></b> and <b><i>Repository &gt; Write</i></b> permission.</span>
        <span class="error" id="error_${keys.bitbucketCloudPassword}"></span>
      </td>
    </tr>
  </props:selectSectionPropertyContent>

  <props:selectSectionPropertyContent value="${keys.authTypeStoredToken}" caption="Refreshable access token">
    <tr>
      <th><label for="${keys.tokenId}">Refreshable access token:<l:star/></label></th>
      <td>
        <c:if test="${empty oauthConnections}">
          <div>There are no Bitbucket connections available to the project.</div>
        </c:if>

        <props:hiddenProperty name="${keys.tokenId}" />
        <span class="error" id="error_${keys.tokenId}"></span>

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
      </td>
    </tr>
  </props:selectSectionPropertyContent>

  <props:selectSectionPropertyContent value="${keys.authTypeVCS}" caption="Use VCS root(-s) credentials">
    <tr><td colspan="2">
      <em>
        TeamCity obtains App password / token based credentials from the VCS root settings.
        This option will not work if the VCS root uses an SSH fetch URL, employs anonymous authentication,
        or authenticates via an API token or regular user password.
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