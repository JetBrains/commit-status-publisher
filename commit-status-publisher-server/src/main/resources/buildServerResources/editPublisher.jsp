<%@include file="publisherFeatureHeader.jsp"%>
    <tbody id="publisherProperties">
      <jsp:include page="${editedPublisherUrl}">
        <jsp:param name="propertiesBean" value="${propertiesBean}"/>
      </jsp:include>
    </tbody>
