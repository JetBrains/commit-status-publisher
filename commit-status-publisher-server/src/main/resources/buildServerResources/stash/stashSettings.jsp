<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<c:if test="${testConnectionSupported}">
  <script>
    $j(document).ready(function() {
      PublisherFeature.showTestConnection();
    });
  </script>
</c:if>
<table style="width: 100%">
  <tr>
    <th><label for="${keys.stashBaseUrl}">Bitbucket Server Base URL: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.stashBaseUrl}" style="width:18em;"/>
      <span class="smallNote">Base URL field in Bitbucket Server settings</span>
      <span class="error" id="error_${keys.stashBaseUrl}"></span>
    </td>
  </tr>
  <l:settingsGroup title="Repository">
    <span class="smallNote">Plugin will recognize Pull Request branches only when <strong><a href="https://confluence.jetbrains.com/display/TCD10/Working+with+Feature+Branches">Branch specification</a></strong> parameter in <strong>VCS Roots</strong> will be configured as below:</span>
    <span class="smallNote"><strong><i>+:refs/pull-requests/(*/merge)</i></strong></span>
    <tr>
      <th><label for="${keys.stashProjectKey}">Project Key: <l:star/></label></th>
      <td>
        <props:textProperty name="${keys.stashProjectKey}" style="width:18em;"/>
      </td>
    </tr>
    <tr>
      <th><label for="${keys.stashRepoName}">Repository: <l:star/></label></th>
      <td>
        <props:textProperty name="${keys.stashRepoName}" style="width:18em;"/>
      </td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Authentication">
    <tr>
      <th><label for="${keys.stashUsername}">Username: <l:star/></label></th>
      <td>
        <props:textProperty name="${keys.stashUsername}" style="width:18em;"/>
      </td>
    </tr>
    <tr>
      <th><label for="${keys.stashPassword}">Password: <l:star/></label></th>
      <td>
        <props:passwordProperty name="${keys.stashPassword}" style="width:18em;"/>
      </td>
    </tr>
  </l:settingsGroup>
</table>

