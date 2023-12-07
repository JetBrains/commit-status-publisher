<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
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

<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List<jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor>"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/>

<%--@elvariable id="canEditProject" type="java.lang.Boolean"--%>

  <tr>
    <!-- must be inside a html tag, as otherwhise jQuery "nextSiblings" from selectProperty tag chokes on the script tag -->
    <script type="text/javascript">
      BS.BBDataCenterCspSettings = {

        connectionToServerUrl: new Map(),
        baseUrlChanged: false,

        onTokenObtained(it) {
          const baseUrlField = $('${keys.stashBaseUrl}');
          if (!BS.BBDataCenterCspSettings.baseUrlChanged || !baseUrlField.value) {
            baseUrlField.value = BS.BBDataCenterCspSettings.connectionToServerUrl.get(it.connectionId);
          }
          BS.AuthTypeTokenSupport.tokenCallback(it);
        },

        onBaseUrlChange() {
          this.baseUrlChanged = true;
        }
      }
    </script>
    <th><label for="${keys.stashBaseUrl}">Bitbucket Server Base URL:</label></th>
    <td>
      <props:textProperty name="${keys.stashBaseUrl}" className="longField" onchange="BS.BBDataCenterCspSettings.onBaseUrlChange();"/>
      <span class="smallNote">
        Base URL field in Bitbucket Server settings.<br/>
        If left blank, the URL will be composed based on the VCS root fetch URL.
      </span>
      <span class="error" id="error_${keys.stashBaseUrl}"></span>
    </td>
  </tr>

  <props:selectSectionProperty name="${keys.authType}" title="Authentication Type" style="width: 28em;">

    <props:selectSectionPropertyContent value="${keys.authTypePassword}" caption="Username / Password">
      <tr>
        <th><label for="${keys.stashUsername}">Username:<l:star/></label></th>
        <td>
          <props:textProperty name="${keys.stashUsername}" className="longField"/>
          <span class="error" id="error_${keys.stashUsername}"></span>
        </td>
      </tr>

      <tr>
        <th><label for="${keys.stashPassword}">Password:<l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.stashPassword}" className="longField"/>
          <span class="error" id="error_${keys.stashPassword}"></span>
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
            <span>There are no Bitbucket Server connections available to the project.</span>
          </c:if>

          <c:set var="canObtainTokens" value="${canEditProject and not project.readOnly}"/>
          <c:set var="connectorType" value="${keys.stashOauthProviderType}"/>
          <oauth:tokenControlsForFeatures
            project="${project}"
            providerTypes="'${connectorType}'"
            tokenIntent="PUBLISH_STATUS"
            canObtainTokens="${canObtainTokens}"
            callback="BS.BBDataCenterCspSettings.onTokenObtained"
            oauthConnections="${oauthConnections}">
            <jsp:attribute name="addCredentialFragment">
              <span class="smallNote connection-note">Add credentials via the
                  <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=${connectorType}'/>" target="_blank" rel="noreferrer">Project Connections</a> page</span>
            </jsp:attribute>
            <jsp:body>
              BS.BBDataCenterCspSettings.connectionToServerUrl.set('${connection.id}', '<bs:forJs>${connection.parameters['bitbucketUrl']}</bs:forJs>');
            </jsp:body>
          </oauth:tokenControlsForFeatures>

          <props:hiddenProperty name="${keys.tokenId}" />
          <span class="error" id="error_${keys.tokenId}"></span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>
    <props:selectSectionPropertyContent value="${keys.authTypeVCS}" caption="Use VCS root(-s) credentials">
      <tr><td colspan="2">
        <em>
          TeamCity obtains password / token based credentials from the VCS root settings.
          This option will not work if the VCS root uses an SSH fetch URL or
          employs anonymous authentication.
        </em>
      </td></tr>
    </props:selectSectionPropertyContent>

    <c:if test="${testConnectionSupported}">
      <script>
        $j(document).ready(function() {
          PublisherFeature.showTestConnection(() => {
            return {
              text: `This test confirms that TeamCity can <strong>pull/read</strong> data from the target repository under the provided credentials.<br/></br/>
                     If builds fail to publish their statuses, check whether the current Bitbucket server user has corresponding <strong>push/write</strong> permissions.`,
              preserveHtml: true
            };
          });
        });
      </script>
    </c:if>

  </props:selectSectionProperty>