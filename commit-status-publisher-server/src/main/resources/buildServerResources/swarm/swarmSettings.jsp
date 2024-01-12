<%@ page import="jetbrains.buildServer.swarm.commitPublisher.SwarmPublisherSettings" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>


<c:set var="urlField" value="<%= SwarmPublisherSettings.PARAM_URL%>"/>
<c:set var="userField" value="<%= SwarmPublisherSettings.PARAM_USERNAME%>"/>
<c:set var="pwdField" value="<%= SwarmPublisherSettings.PARAM_PASSWORD%>"/>
<c:set var="createSwarmTestField" value="<%= SwarmPublisherSettings.PARAM_CREATE_SWARM_TEST%>"/>

<tr>
  <th><label for="${urlField}">Perforce Swarm URL:<l:star/></label></th>
  <td>
    <props:textProperty name="${urlField}" className="mediumField"/>
    <span class="error" id="error_${urlField}"></span>
  </td>
</tr>

<tr>
  <th><label for="${userField}">Username:<l:star/></label></th>
  <td>
    <props:textProperty name="${userField}" className="mediumField"/>
    <span class="error" id="error_${userField}"></span>
  </td>
</tr>

<tr>
  <th><label for="${pwdField}">Ticket:<l:star/></label></th>
  <td>
    <props:passwordProperty name="${pwdField}" className="mediumField"/>
    <span class="smallNote">Get the ticket with the command <code>p4 login -a -p</code></span>
    <span class="error" id="error_${pwdField}"></span>
  </td>
</tr>

<tr>
  <th><label for="${createSwarmTestField}">Create Swarm Tests:</label><bs:help file="integrating-with-helix-swarm"/></th>
  <td>
    <div style="display: flex; gap: 5px; align-items: flex-start;">
      <props:checkboxProperty name="${createSwarmTestField}" style="margin-top: 5px" />
      <span class="error" id="error_${createSwarmTestField}"></span>
      <span class="note">
        If this setting is enabled, TeamCity will create new Swarm tests to publish build statuses.
        Enter username and ticket of a user with administrator permissions to employ this approach.
        <p>
          Leave this setting disabled to use existing Swarm tests instead of creating new ones.

          This approach allows the Commit Status Publisher to use regular users' credentials.

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