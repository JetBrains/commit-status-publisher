<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<table style="width: 100%">
  <tr>
    <th><label for="${keys.stashBaseUrl}">Stash base url: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.stashBaseUrl}" style="width:18em;"/>
      <span class="error" id="error_${keys.stashBaseUrl}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.stashUsername}">Stash username: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.stashUsername}" style="width:18em;"/>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.stashPassword}">Stash password: <l:star/></label></th>
    <td>
      <props:passwordProperty name="${keys.stashPassword}" style="width:18em;"/>
    </td>
  </tr>
</table>

