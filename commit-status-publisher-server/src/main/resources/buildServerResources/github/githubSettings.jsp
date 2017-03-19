<%@ page import="jetbrains.buildServer.web.openapi.PlaceId" %>
<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
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

<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.Map"/>

<c:url value="/oauth/github/token.html" var="getTokenPage"/>
<c:set var="cameFromUrl" value="${empty param['cameFromUrl'] ? pageUrl : param['cameFromUrl']}"/>
<c:set var="getTokenPage" value="${getTokenPage}?cameFromUrl=${util:urlEscape(cameFromUrl)}"/>


<c:set var="oauth_connection_fragment">
  <c:forEach items="${oauthConnections.keySet()}" var="connection">
    <c:set var="title">Acquire an access token from <c:out value="${connection.parameters['gitHubUrl']}"/></c:set>
    <span class="githubRepoControl"><i class="icon-magic" style="cursor:pointer;" title="${title}" onclick="BS.GitHubAccessTokenPopup.showPopup(this, '${connection.id}')"></i></span>
  </c:forEach>
</c:set>

  <tr>
    <th><label for="${keys.serverKey}">GitHub URL: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.serverKey}" className="longField"/>
      <span class="error" id="error_${keys.serverKey}"></span>
      <span class="smallNote">
        Format: <strong>http[s]://&lt;host&gt;[:&lt;port&gt;]/api/v3</strong>
        for <a href="https://support.enterprise.github.com/entries/21391237-Using-the-API" target="_blank">GitHub Enterprise</a>
      </span>
    </td>
  </tr>

 <props:selectSectionProperty name="${keys.authenticationTypeKey}" title="Authentication Type">

    <props:selectSectionPropertyContent value="${keys.authenticationTypeTokenValue}" caption="Access Token">
      <tr>
        <th><label for="${keys.accessTokenKey}">Access Token: <l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.accessTokenKey}" className="mediumField"/>
            ${oauth_connection_fragment}
          <props:hiddenProperty name="${keys.OAuthUserKey}" />
          <props:hiddenProperty name="${keys.OAuthProviderIdKey}" />
          <span class="error" id="error_${keys.accessTokenKey}"></span>
          <span class="smallNote">
            GitHub <a href="https://github.com/settings/applications" target="_blank">Personal Access Token</a>
            <br />
            It is required to have the following permissions:
            <strong><em>repo:status</em></strong> and
            <strong><em>public_repo</em></strong> or <strong><em>repo</em></strong> depending on the repository type
          </span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

    <props:selectSectionPropertyContent value="${keys.authenticationTypePasswordValue}" caption="Password">
      <tr>
        <th><label for="${keys.userNameKey}">GitHub Username: <l:star/></label></th>
        <td>
          <props:textProperty name="${keys.userNameKey}" className="mediumField"/>
          <span class="error" id="error_${keys.userNameKey}"></span>
        </td>
      </tr>
      <tr>
        <th><label for="${keys.passwordKey}">GitHub Password: <l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.passwordKey}" className="mediumField"/>
          <span class="error" id="error_${keys.passwordKey}"></span>

          <c:if test="${testConnectionSupported}">
            <script>
              $j(document).ready(function() {
                PublisherFeature.showTestConnection();
              });
            </script>
          </c:if>

          <script type="text/javascript">

            BS.GitHubAccessTokenPopup = new BS.Popup('gitHubGetToken', {
              url: "${getTokenPage}",
              method: "get",
              hideDelay: 0,
              hideOnMouseOut: false,
              hideOnMouseClickOutside: true
            });

            BS.GitHubAccessTokenPopup.showPopup = function(nearestElement, connectionId) {
              this.options.parameters = "projectId=${project.externalId}&connectionId=" + connectionId + "&showMode=popup";
              var that = this;

              window.GitHubTokenContentUpdater = function() {
                that.hidePopup(0);
                that.showPopupNearElement(nearestElement);
              };
              this.showPopupNearElement(nearestElement);
            };

            window.getOAuthTokenCallback = function(cre) {
              if (cre != null) {
                $('${keys.OAuthUserKey}').value = cre.oauthLogin;
                $('${keys.OAuthProviderIdKey}').value = cre.oauthProviderId;
                $('${keys.accessTokenKey}').value = '******************************'
              }
              BS.GitHubAccessTokenPopup.hidePopup(0, true);
            };
          </script>

          <style type="text/css">
            .tc-icon_github,
            .tc-icon_github-enterprise {
              cursor: pointer;
            }

            a > .tc-icon_github_disabled {
              text-decoration: none;
            }
          </style>

        </td>
      </tr>
    </props:selectSectionPropertyContent>

  </props:selectSectionProperty>

