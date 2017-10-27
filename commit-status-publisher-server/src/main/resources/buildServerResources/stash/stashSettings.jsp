<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.stash.ui.UpdateChangesConstants"/>

  <tr>
    <th><label for="${keys.stashBaseUrl}">Bitbucket Server Base URL: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.stashBaseUrl}" className="longField"/>
      <span class="smallNote">Base URL field in Bitbucket Server settings</span>
      <span class="error" id="error_${keys.stashBaseUrl}"></span>
    </td>
  </tr>

  <props:selectSectionProperty name="${keys.authenticationTypeKey}" title="Authentication Type">

    <props:selectSectionPropertyContent value="${keys.authenticationTypeTokenValue}" caption="Access Token">
      <tr>
        <th><label for="${keys.stashToken}">Token: <l:star/></label></th>
        <td>
          <props:textProperty name="${keys.stashToken}" className="mediumField"/>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

    <props:selectSectionPropertyContent value="${keys.authenticationTypePasswordValue}" caption="Password">
      <tr>
        <th><label for="${keys.stashUsername}">Username: <l:star/></label></th>
        <td>
          <props:textProperty name="${keys.stashUsername}" className="mediumField"/>
        </td>
      </tr>

      <tr>
        <th><label for="${keys.stashPassword}">Password: <l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.stashPassword}" className="mediumField"/>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

  </props:selectSectionProperty>

  <c:if test="${testConnectionSupported}">
    <script>
      $j(document).ready(function() {
        PublisherFeature.showTestConnection("This ensures that the repository is reachable under the provided credentials.\nIf status publishing still fails, it can be due to insufficient permissions of the corresponding BitBucket Server user.");
      });
    </script>
  </c:if>


