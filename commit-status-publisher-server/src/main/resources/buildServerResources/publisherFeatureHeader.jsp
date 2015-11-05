<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="constants" class="jetbrains.buildServer.commitPublisher.Constants" scope="request" />

<c:url value="${publisherSettingsUrl}" var="settingsUrl"/>
<script type="text/javascript">
  PublisherFeature = {
    showPublisherSettings: function() {
      var url = '${settingsUrl}?${constants.publisherIdParam}=' + $('${constants.publisherIdParam}').value  + "&projectId=${projectId}";
      $j.get(url, function(xhr) {
        $j("#publisherProperties").html(xhr);
      });
      return false;
    }
  };
</script>
<tr>
  <th><label for="${constants.vcsRootIdParam}">VCS Root:&nbsp;<l:star/></label></th>
  <td>
    <props:selectProperty name="${constants.vcsRootIdParam}">
      <c:forEach var="vcsRoot" items="${vcsRoots}">
        <props:option value="${vcsRoot.id}"><bs:trim maxlength="60"><c:out value="${vcsRoot.name}"/></bs:trim></props:option>
      </c:forEach>
    </props:selectProperty>
    <span class="error" id="error_${constants.vcsRootIdParam}"></span>
  </td>
</tr>
<tr>
  <th><label for="${constants.publisherIdParam}">Publisher:&nbsp;<l:star/></label></th>
  <td>
    <props:selectProperty name="${constants.publisherIdParam}" onchange="PublisherFeature.showPublisherSettings()">
      <c:forEach var="publisher" items="${publishers}">
        <props:option value="${publisher.id}"><c:out value="${publisher.name}"/></props:option>
      </c:forEach>
    </props:selectProperty>
    <span class="error" id="error_${constants.publisherIdParam}"></span>
  </td>
</tr>
