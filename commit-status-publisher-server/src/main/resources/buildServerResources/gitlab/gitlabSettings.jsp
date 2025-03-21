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

<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="oauth" tagdir="/WEB-INF/tags/oauth" %>



<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List"/>
<jsp:useBean id="refreshTokenSupported" scope="request" type="java.lang.Boolean"/>


<props:selectSectionProperty name="authType" title="Authentication Type:" style="width: 28em;">
  <props:selectSectionPropertyContent value="token" caption="Personal access token">
    <tr>
      <th><label for="${keys.gitlabToken}">Access Token:<l:star/></label></th>
      <td>
        <props:passwordProperty name="${keys.gitlabToken}" className="longField"/>
        <span class="smallNote">
              Can be found at <strong>/profile/account</strong> in GitLab
          </span>
        <span class="error" id="error_${keys.gitlabToken}"></span>
      </td>
    </tr>
  </props:selectSectionPropertyContent>
  <props:selectSectionPropertyContent value="storedToken" caption="Refreshable access token">
    <tr>
      <th>
        <label for="refreshabletoken">Refreshable access token:</label>
      </th>
      <td>
        <c:if test="${empty oauthConnections}">
          <div>There are no GitLab OAuth 2.0 connections available to the project.</div>
        </c:if>

        <props:hiddenProperty name="tokenId" />
        <span class="error" id="error_tokenId"></span>

        <c:set var="canObtainTokens" value="${canEditProject and not project.readOnly}"/>
        <oauth:tokenControlsForFeatures
          project="${project}"
          providerTypes="'GitLabCom', 'GitLabCEorEE'"
          tokenIntent="PUBLISH_STATUS"
          canObtainTokens="${canObtainTokens}"
          callback="BS.AuthTypeTokenSupport.tokenCallback"
          oauthConnections="${oauthConnections}">
          <jsp:attribute name="addCredentialFragment">
            <span class="smallNote connection-note">
              <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=GitLabCom'/>" target="_blank" rel="noreferrer">
                Add GitLab.com
              </a>
              or either
              <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=GitLabCEorEE'/>" target="_blank" rel="noreferrer">
                add GitLab CE/EE
              </a>
              credentials via the Project Connections page
            </span>
          </jsp:attribute>
        </oauth:tokenControlsForFeatures>
      </td>
    </tr>
  </props:selectSectionPropertyContent>

  <props:selectSectionPropertyContent value="vcsRoot" caption="Use VCS root(-s) credentials">
    <tr><td colspan="2">
      <em>
        TeamCity obtains token based credentials from the VCS root settings.
        This option will not work if the VCS root uses an SSH fetch URL,
        employs anonymous authentication or uses
        an actual password
        of the user rather than a token.
      </em>
    </td></tr>
  </props:selectSectionPropertyContent>

</props:selectSectionProperty>

<l:settingsGroup title="On-premises GitLab installation" />
<tr>
  <th><label for="${keys.gitlabServer}">GitLab API URL:</label></th>
  <td>
    <props:textProperty name="${keys.gitlabServer}" className="longField"/>
    <span class="smallNote">
      Format: <strong>http[s]://&lt;hostname&gt;[:&lt;port&gt;]/api/v4</strong><br>
      If left blank, the URL will be composed based on the VCS root fetch URL.
    </span>
    <span class="error" id="error_${keys.gitlabServer}"></span>
  </td>
</tr>

<c:if test="${testConnectionSupported}">
  <script>
    $j(document).ready(function() {
      PublisherFeature.showTestConnection();
    });
  </script>
</c:if>