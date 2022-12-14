<%@ page import="jetbrains.buildServer.serverSide.oauth.bitbucket.BitBucketOAuthProvider" %>
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
<jsp:useBean id="oauthConnections" scope="request" type="java.util.Map"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/>

  <tr>
    <th><label for="${keys.stashBaseUrl}">Bitbucket Server Base URL:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.stashBaseUrl}" className="longField"/>
      <span class="smallNote">Base URL field in Bitbucket Server settings</span>
      <span class="error" id="error_${keys.stashBaseUrl}"></span>
    </td>
  </tr>

  <props:selectSectionProperty name="${keys.authType}" title="Authentication Type">

    <props:selectSectionPropertyContent value="${keys.authTypePassword}" caption="Username / Password">
      <tr>
        <th><label for="${keys.stashUsername}">Username:<l:star/></label></th>
        <td>
          <props:textProperty name="${keys.stashUsername}" className="mediumField"/>
          <span class="error" id="error_${keys.stashUsername}"></span>
        </td>
      </tr>

      <tr>
        <th><label for="${keys.stashPassword}">Password:<l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.stashPassword}" className="mediumField"/>
          <span class="error" id="error_${keys.stashPassword}"></span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

    <props:selectSectionPropertyContent value="${keys.authTypeAccessToken}" caption="Access Token">
      <script type="text/javascript">
        BS.BitbucketServerTokenSupport = {
          updateTokenMessage: () => {
            const tokenValue = $('${keys.tokenId}').value;
            if (tokenValue === null || tokenValue.trim().length == 0) {
              $('message_no_token').show();
              $('message_we_have_token').hide();
            } else {
              $('message_no_token').hide();
              $('message_we_have_token').show();
            }
            $j('#error_${keys.tokenId}').empty();
          },

          tokenCallback: (it) => {
            $('${keys.tokenId}').value = it.tokenId;
            BS.BitbucketServerTokenSupport.updateTokenMessage();
          }
        };

        BS.BitbucketServerTokenSupport.updateTokenMessage();
      </script>

      <tr>
        <th><label for="${keys.tokenId}">Access Token:<l:star/></label></th>
        <td>
          <span class="access-token-note" id="message_no_token">No access token configured.</span>
          <span class="access-token-note" id="message_we_have_token">There is an access token configured.</span>
          <c:if test="${empty oauthConnections}">
            <br/>
            <span>There are no Bitbucket Server connections available to the project.</span>
          </c:if>

          <props:hiddenProperty name="${keys.tokenId}" />
          <span class="error" id="error_${keys.tokenId}"></span>

          <c:forEach items="${oauthConnections.keySet()}" var="connection">
            <div class="token-connection">
              <span class="token-connection-diplay-name">${connection.connectionDisplayName}</span>
              <oauth:obtainToken connection="${connection}" className="btn btn_small token-connection-button" callback="BS.BitbucketServerTokenSupport.tokenCallback">
                Acquire
              </oauth:obtainToken>
            </div>
          </c:forEach>

          <c:set var="connectorType" value="${keys.stashOauthProviderType}"/>
          <span class="smallNote connection-note">Add credentials via the
                <a href="<c:url value='/admin/editProject.html?projectId=${project.externalId}&tab=oauthConnections#addDialog=${connectorType}'/>" target="_blank" rel="noreferrer">Project Connections</a> page</span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

    <c:if test="${testConnectionSupported}">
      <script>
        $j(document).ready(function() {
          PublisherFeature.showTestConnection("This ensures that the repository is reachable under the provided credentials.\nIf status publishing still fails, it can be due to insufficient permissions of the corresponding BitBucket Server user.");
        });
      </script>
    </c:if>

  </props:selectSectionProperty>

<style type="text/css">
  .token-connection {
    clear: right;
    padding-top: 0.25em;
    width: 60%;
  }

  .token-connection-diplay-name {
    float: left;
    margin-left: 1em;
  }

  .token-control {
    float: right;
  }

  .token-connection-button {
    font-style: normal;
  }

  .access-token-note {
    font-style: italic;
  }

  .connection-note {
    clear: right;
  }
</style>

