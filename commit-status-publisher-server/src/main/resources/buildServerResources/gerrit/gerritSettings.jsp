<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<table style="width: 100%">
  <tr>
    <th><label for="${keys.gerritServer}">Gerrit server: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritServer}" style="width:18em;"/>
      <span class="error" id="error_${keys.gerritServer}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritProject}">Gerrit project: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritProject}" style="width:18em;"/>
      <span class="error" id="error_${keys.gerritProject}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.sshKey}">Ssh Key: <l:star/></label></th>
    <td>
      <admin:sshKeys projectId="${projectId}"/>
      <span class="error" id="error_${keys.sshKey}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritSuccessVote}">Successful build vote: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritSuccessVote}" style="width:18em;"/>
      <span class="error" id="error_${keys.gerritSuccessVote}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritFailureVote}">Failed build vote: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritFailureVote}" style="width:18em;"/>
      <span class="error" id="error_${keys.gerritFailureVote}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritUsername}">Gerrit username: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritUsername}" style="width:18em;"/>
      <span class="error" id="error_${keys.gerritUsername}"></span>
    </td>
  </tr>
</table>
