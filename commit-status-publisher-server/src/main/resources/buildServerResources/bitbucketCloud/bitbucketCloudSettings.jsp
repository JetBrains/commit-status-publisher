<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<table style="width: 100%">
  <tr>
    <th><label for="bitbucketUsername">BitBucket username: <l:star/></label></th>
    <td>
      <props:textProperty name="bitbucketUsername" style="width:18em;"/>
    </td>
  </tr>

  <tr>
    <th><label for="secure:bitbucketPassword">BitBucket password: <l:star/></label></th>
    <td>
      <props:passwordProperty name="secure:bitbucketPassword" style="width:18em;"/>
    </td>
  </tr>
</table>

