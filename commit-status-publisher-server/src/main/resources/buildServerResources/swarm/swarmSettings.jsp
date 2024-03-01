<%@ page import="jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings" %>
<%@ page import="jetbrains.buildServer.swarm.SwarmConstants" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop" %>

<c:set var="urlField" value="<%= SwarmPublisherSettings.PARAM_URL%>"/>
<c:set var="userField" value="<%= SwarmPublisherSettings.PARAM_USERNAME%>"/>
<c:set var="pwdField" value="<%= SwarmPublisherSettings.PARAM_PASSWORD%>"/>
<c:set var="createSwarmTestField" value="<%= SwarmPublisherSettings.PARAM_CREATE_SWARM_TEST%>"/>
<c:set var="commentOnEventsField" value="<%= SwarmPublisherSettings.PARAM_COMMENT_ON_EVENTS%>"/>
<c:set var="propCommentSelectively" value="<%= SwarmConstants.FEATURE_ENABLE_COMMENTS_SELECTIVELY%>"/>
<c:set var="commentSelectively" value="${intprop:getBooleanOrTrue(propCommentSelectively)}"/>

<tr>
  <th><label for="${urlField}">Perforce Swarm URL:<l:star/></label></th>
  <td>
    <props:textProperty name="${urlField}" className="longField"/>
    <span class="error" id="error_${urlField}"></span>
  </td>
</tr>

<tr>
  <th><label for="${userField}">Username:<l:star/></label></th>
  <td>
    <props:textProperty name="${userField}" className="longField"/>
    <span class="error" id="error_${userField}"></span>
  </td>
</tr>

<tr>
  <th><label for="${pwdField}">Ticket:<l:star/></label></th>
  <td>
    <props:passwordProperty name="${pwdField}" className="longField"/>
    <span class="smallNote">Get the ticket with the command <code>p4 login -a -p</code></span>
    <span class="error" id="error_${pwdField}"></span>
  </td>
</tr>

<c:if test="${commentSelectively}">
  <tr class="noChangeIndicator">
    <th><label for="${commentOnEventsField}">Code Review Comments:</label></th>
    <td>
      <div class="checkBoxAndDescription">
        <props:checkboxProperty name="${commentOnEventsField}" uncheckedValue="false" style="margin-top: 5px" />
        <span class="error" id="error_${commentOnEventsField}"></span>
        <span class="note">If enabled, TeamCity will add comments to a related Swarm review when a build fails or finishes successfully.</span>
      </div>
    </td>
  </tr>
</c:if>

<tr>
  <th><label for="${createSwarmTestField}">Create Swarm Tests:</label><bs:help file="integrating-with-helix-swarm"/></th>
  <td>
    <div class="checkBoxAndDescription">
      <props:checkboxProperty name="${createSwarmTestField}" style="margin-top: 5px" />
      <span class="error" id="error_${createSwarmTestField}"></span>
      <span class="note">
        Choose whether TeamCity should create new Swarm tests or use existing ones to publish build statuses.
        Creating new Swarm tests requires a username and ticket of a user with administrative permissions.
        <p>
          See <bs:helpLink file="integrating-with-helix-swarm">this documentation article</bs:helpLink> to learn how to set up Swarm workflows and tests for TeamCity.
        </p>
    </span>
    </div>
  </td>
</tr>

<script>
  $j(document).ready(function() {
    PublisherFeature.showTestConnection("Successfully authenticated at the Perforce Swarm server.");
  });
</script>

<style type="text/css">
  td div.checkBoxAndDescription {
    display: flex;
    gap: 5px;
    align-items: flex-start;
  }

  tr.noChangeIndicator.valueChanged {
    border-left: none;
  }
</style>
