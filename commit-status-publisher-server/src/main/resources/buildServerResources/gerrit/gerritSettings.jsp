<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>

  <tr>
    <th><label for="${keys.gerritServer}">Gerrit Server: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritServer}" className="longField"/>
      <span class="smallNote">Format: <strong>&lt;host&gt;[:&lt;port&gt;]</strong></span>
      <span class="error" id="error_${keys.gerritServer}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritUsername}">Gerrit Username: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritUsername}" className="mediumField"/>
      <span class="error" id="error_${keys.gerritUsername}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.sshKey}">SSH Key: <l:star/></label></th>
    <td>
      <admin:sshKeys projectId="${projectId}"/>
      <span class="error" id="error_${keys.sshKey}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritProject}">Gerrit Project Name: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritProject}" className="mediumField"/>
      <span class="error" id="error_${keys.gerritProject}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritLabel}">Gerrit Label: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritLabel}" className="mediumField" />
      <span class="error" id="error_${keys.gerritLabel}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritSuccessVote}">Successful Build Vote: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritSuccessVote}" className="mediumField"/>
      <span class="error" id="error_${keys.gerritSuccessVote}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritFailureVote}">Failed Build Vote: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritFailureVote}" className="mediumField"/>
      <span class="error" id="error_${keys.gerritFailureVote}"></span>
      <c:if test="${testConnectionSupported}">
        <script>
          $j(document).ready(function() {
            PublisherFeature.showTestConnection();
          });
        </script>
      </c:if>
    </td>
  </tr>
