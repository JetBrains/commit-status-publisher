<%@include file="publisherFeatureHeader.jsp"%>
<tr>
  <td colspan="2" style="padding: 0;">
    <div id="publisherProperties">
      <jsp:include page="${editedPublisherUrl}">
        <jsp:param name="propertiesBean" value="${propertiesBean}"/>
      </jsp:include>
    </div>
  </td>
</tr>