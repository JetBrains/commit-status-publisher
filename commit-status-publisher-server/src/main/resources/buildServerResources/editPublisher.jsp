<%@include file="publisherFeatureHeader.jsp"%>
<tr>
  <td colspan="2">
    <bs:refreshable containerId="publisherProperties" pageUrl="dynamic">
      <jsp:include page="${editedPublisherUrl}">
        <jsp:param name="propertiesBean" value="${propertiesBean}"/>
      </jsp:include>
    </bs:refreshable>
  </td>
</tr>