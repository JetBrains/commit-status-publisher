<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="oauth" tagdir="/WEB-INF/tags/oauth" %>


<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants"/>
<jsp:useBean id="oauthConnections" scope="request" type="java.util.List"/>
<jsp:useBean id="refreshTokenSupported" scope="request" type="java.lang.Boolean"/>

<%--@elvariable id="canEditProject" type="java.lang.Boolean"--%>
<%--@elvariable id="project" type="jetbrains.buildServer.serverSide.SProject"--%>

<c:url value="/oauth/github/token.html" var="getTokenPage"/>
<c:set var="cameFromUrl" value="${empty param['cameFromUrl'] ? pageUrl : param['cameFromUrl']}"/>
<c:set var="getTokenPage" value="${getTokenPage}?cameFromUrl=${util:urlEscape(cameFromUrl)}"/>


<c:set var="oauth_connection_fragment">
  <c:forEach items="${oauthConnections}" var="connection">
    <c:if test="${'GitHubApp' != connection.oauthProvider.type}">
      <c:set var="title">
        Acquire an access token from <c:out value="${connection.parameters['gitHubUrl']}"/> (<c:out value="${connection.connectionDisplayName}"/>)
      </c:set>
      <span class="githubRepoControl"><i class="icon-magic" style="cursor:pointer;" title="${title}" onclick="BS.GitHubAccessTokenPopup.showPopup(this, '${connection.id}')"></i></span>
    </c:if>
  </c:forEach>
</c:set>

  <tr>
    <th><label for="${keys.serverKey}">GitHub URL:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.serverKey}" className="longField"/>
      <span class="error" id="error_${keys.serverKey}"></span>
      <span class="smallNote">
        Format: <strong>http[s]://&lt;host&gt;[:&lt;port&gt;]/api/v3</strong>
        for <a href="https://developer.github.com/enterprise/v3/" target="_blank" rel="noreferrer">GitHub Enterprise</a>
      </span>
    </td>
  </tr>

 <props:selectSectionProperty name="${keys.authenticationTypeKey}" title="Authentication Type" style="width: 28em;">

    <props:selectSectionPropertyContent value="${keys.authenticationTypeTokenValue}" caption="Access Token">
      <tr>
        <th><label for="${keys.accessTokenKey}">Access Token:<l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.accessTokenKey}" className="longField" onchange="PublisherFeature.resetAccessTokenNote();"/>
            ${oauth_connection_fragment}
          <props:hiddenProperty name="${keys.OAuthUserKey}" />
          <props:hiddenProperty name="${keys.OAuthProviderIdKey}" />
          <span class="error" id="error_${keys.accessTokenKey}"></span>
          <span class="smallNote">
            <span id="note_oauth_token">OAuth access token issued for GitHub user <strong id="note_oauth_user"></strong></span>
            <span id="note_personal_token">GitHub <a href="https://github.com/settings/applications" target="_blank" rel="noreferrer">Personal Access Token</a></span>
            <br />
            It is required to have the following permissions:
            <strong><em>repo:status</em></strong> and
            <strong><em>public_repo</em></strong> or <strong><em>repo</em></strong> depending on the repository type
          </span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

   <c:if test='${!isAcquiringTokensDisabled}'>
     <props:selectSectionPropertyContent value="${keys.authentificationTypeGitHubAppTokenValue}" caption="GitHub App access token">
       <%@include file="/admin/_tokenSupport.jspf"%>
       <tr>
         <th>
           <label for="${keys.tokenIdKey}">GitHub App Token:</label>
         </th>
         <td>
           <span class="access-token-note" id="message_no_token">No access token configured.</span>
           <span class="access-token-note" id="message_we_have_token"></span>

           <c:if test="${!refreshTokenSupported}">
             <br/>
             <span>There are no GitHub App connections available to the project.</span>
           </c:if>

           <c:set var="connectorType" value="GitHubApp"/>
           <c:set var="canObtainTokens" value="${canEditProject and not project.readOnly}"/>

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

           <props:hiddenProperty name="${keys.tokenIdKey}" />
           <span class="error" id="error_${keys.tokenIdKey}"></span>
         </td>
       </tr>
     </props:selectSectionPropertyContent>
   </c:if>

   <props:selectSectionPropertyContent value="${keys.authenticationTypePasswordValue}" caption="Password">
      <tr>
        <td colspan="2">
          <em>
            Please note that username/password authentication is now deprecated in GitHub.com for GraphQL and REST API calls and will be completely disabled soon.
            Consider using access tokens instead.
          </em>
        </td>
      </tr>
      <tr>
        <th><label for="${keys.userNameKey}">GitHub Username:<l:star/></label></th>
        <td>
          <props:textProperty name="${keys.userNameKey}" className="longField"/>
          <span class="error" id="error_${keys.userNameKey}"></span>
        </td>
      </tr>
      <tr>
        <th><label for="${keys.passwordKey}">GitHub Password:<l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.passwordKey}" className="longField"/>
          <span class="error" id="error_${keys.passwordKey}"></span>

          <c:if test="${testConnectionSupported}">
            <script>
              $j(document).ready(function() {
                PublisherFeature.showTestConnection(() => {
                  const selectedAuthType = $('${keys.authenticationTypeKey}').value;
                  if (selectedAuthType === '${keys.authentificationTypeGitHubAppTokenValue}' || selectedAuthType === 'vcsRoot') {
                    return {
                      text: `This test confirms that TeamCity can <strong>pull/read</strong> data from the target repository under the provided credentials.<br/></br/>
                             If builds fail to publish their statuses, check whether the current credentials have corresponding <strong>push/write</strong> permissions.`,
                      preserveHtml: true
                    };
                  } else {
                    return {
                      text: "",
                      preserveHtml: false
                    };
                  }
                });
              });
            </script>
          </c:if>

          <script type="text/javascript">

            PublisherFeature.updateAccessTokenNote = function() {
              if ($('${keys.OAuthUserKey}').value == '') {
                $j('#note_oauth_token').hide();
                $j('#note_personal_token').show();
              } else {
                $j('#note_personal_token').hide();
                $j('#note_oauth_user').text($('${keys.OAuthUserKey}').value);
                $j('#note_oauth_token').show();
              }
            };

            PublisherFeature.resetAccessTokenNote = function() {
              $('${keys.OAuthUserKey}').value = '';
              PublisherFeature.updateAccessTokenNote();
            };

            BS.GitHubCspSettings = {
              onVcsRootChanged: function() {
                BS.AuthTypeTokenSupport.disableStoredTokenWhenNoRootSelected('vcsRootId', '${keys.authenticationTypeKey}', 'error_${keys.authenticationTypeKey}');
              }
            };

            $j(document).ready(() => {
              $('${keys.vcsRootId}').onchange = () => {
                BS.GitHubCspSettings.onVcsRootChanged();
              };
              BS.GitHubCspSettings.onVcsRootChanged();
              PublisherFeature.updateAccessTokenNote();
            });

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

              window.TokenContentUpdater = function() {
                that.hidePopup(0);
                that.showPopupNearElement(nearestElement);
              };
              this.showPopupNearElement(nearestElement);
            };

            window.getOAuthTokenCallback = function(cre) {
              if (cre != null) {
                $('${keys.OAuthUserKey}').value = cre.oauthLogin;
                $('${keys.OAuthProviderIdKey}').value = cre.oauthProviderId;
                $('${keys.accessTokenKey}').value = '******************************';
                PublisherFeature.updateAccessTokenNote();
              }
              BS.GitHubAccessTokenPopup.hidePopup(0, true);
            };

            window.getRepositoryUrl = function () {
              const selectedId = $('${keys.vcsRootId}').value;
              const vcsRoot = PublisherFeature.vcsRoots.get(selectedId);
              return (vcsRoot) ? vcsRoot.url : null;
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

   <props:selectSectionPropertyContent value="vcsRoot" caption="Use VCS root(-s) credentials">
     <tr><td colspan="2">
       <em>
         TeamCity obtains token based credentials from the VCS root settings.
         This option will not work if the VCS root uses an SSH fetch URL,
         employs anonymous authentication or uses
         <a href="https://docs.github.com/en/rest/overview/authenticating-to-the-rest-api?apiVersion=2022-11-28#authenticating-with-username-and-password" target="_blank" rel="noreferrer">
           an actual password
         </a>
         of the user rather than a token.
       </em>
     </td></tr>
   </props:selectSectionPropertyContent>

  </props:selectSectionProperty>