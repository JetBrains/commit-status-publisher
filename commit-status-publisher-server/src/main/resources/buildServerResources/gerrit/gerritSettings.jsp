<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<table style="width: 100%">
  <tr>
    <th><label for="gerritServer">Gerrit server: <l:star/></label></th>
    <td>
      <props:textProperty name="gerritServer" style="width:18em;"/>
      <span class="error" id="error_gerritServer"></span>
    </td>
  </tr>

  <tr>
    <th><label for="gerritProject">Gerrit project: <l:star/></label></th>
    <td>
      <props:textProperty name="gerritProject" style="width:18em;"/>
      <span class="error" id="error_gerritProject"></span>
    </td>
  </tr>

  <tr>
    <th><label for="successVote">Successful build vote: <l:star/></label></th>
    <td>
      <props:textProperty name="successVote" style="width:18em;"/>
      <span class="error" id="error_successVote"></span>
    </td>
  </tr>

  <tr>
    <th><label for="failureVote">Failed build vote: <l:star/></label></th>
    <td>
      <props:textProperty name="failureVote" style="width:18em;"/>
      <span class="error" id="error_failureVote"></span>
    </td>
  </tr>

  <tr>
    <th><label for="gerritUsername">Gerrit username: <l:star/></label></th>
    <td>
      <props:textProperty name="gerritUsername" style="width:18em;"/>
      <span class="error" id="error_gerritUsername"></span>
    </td>
  </tr>
</table>
