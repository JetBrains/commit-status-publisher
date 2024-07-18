<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>



<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.gitee.GiteeConstants"/>


<props:selectSectionProperty name="authType" title="Authentication Type:" style="width: 28em;">
  <props:selectSectionPropertyContent value="token" caption="Personal access token">
    <tr>
      <th><label for="${keys.token}">Access Token:<l:star/></label></th>
      <td>
        <props:passwordProperty name="${keys.token}" className="longField"/>
        <span class="smallNote">
              Can be found at <strong>/profile/account</strong> in gitee
          </span>
        <span class="error" id="error_${keys.token}"></span>
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

<l:settingsGroup title="On-premises Gitee installation" />
<tr>
  <th><label for="${keys.server}">Gitee API URL:</label></th>
  <td>
    <props:textProperty name="${keys.server}" className="longField"/>
    <span class="smallNote">
      Format: <strong>http[s]://&lt;hostname&gt;[:&lt;port&gt;]/api/v5</strong><br>
      If left blank, the URL will be composed based on the VCS root fetch URL.
    </span>
    <span class="error" id="error_${keys.server}"></span>
  </td>
</tr>

<c:if test="${testConnectionSupported}">
  <script>
    $j(document).ready(function() {
      PublisherFeature.showTestConnection();
    });
  </script>
</c:if>