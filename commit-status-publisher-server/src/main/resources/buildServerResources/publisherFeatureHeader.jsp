<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
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
      $j('#publisherSettingsProgress').show();
      $j.get(url, function(xhr) {
        $j('#publisherSettingsProgress').hide();
        $j("#publisherProperties").html(xhr);
      });
      return false;
    }
  };
</script>
<c:if test="${fn:length(vcsRoots) == 0}">
<tr>
  <td colspan="2">
    <span class="error">There are no VCS roots configured.</span>
  </td>
</tr>
</c:if>
<c:if test="${fn:length(vcsRoots) > 0}">
  <c:if test="${fn:length(vcsRoots) > 1}">
    <tr>
      <th><label for="${constants.vcsRootIdParam}">VCS Root:&nbsp;<l:star/></label></th>
      <td>
        <props:selectProperty name="${constants.vcsRootIdParam}" enableFilter="true">
          <c:forEach var="vcsRoot" items="${vcsRoots}">
            <props:option value="${vcsRoot.externalId}"><c:out value="${vcsRoot.name}"/></props:option>
          </c:forEach>
        </props:selectProperty>
        <span class="error" id="error_${constants.vcsRootIdParam}"></span>
        <span class="smallNote">Choose a repository to use for publishing a build status.</span>
      </td>
    </tr>
  </c:if>
  <tr>
    <th>
      <label for="${constants.publisherIdParam}">Publisher:&nbsp;<l:star/></label>
    </th>
    <td>
      <props:selectProperty name="${constants.publisherIdParam}" onchange="PublisherFeature.showPublisherSettings()" enableFilter="true">
        <c:forEach var="publisher" items="${publishers}">
          <props:option value="${publisher.id}"><c:out value="${publisher.name}"/></props:option>
        </c:forEach>
      </props:selectProperty> <forms:progressRing id="publisherSettingsProgress" style="float:none; display: none;"/>
      <span class="error" id="error_${constants.publisherIdParam}"></span>

      <c:if test="${fn:length(vcsRoots) == 1}">
        <props:hiddenProperty name="${constants.vcsRootIdParam}" value="${vcsRoots[0].externalId}"/>
      </c:if>
    </td>
  </tr>
</c:if>
