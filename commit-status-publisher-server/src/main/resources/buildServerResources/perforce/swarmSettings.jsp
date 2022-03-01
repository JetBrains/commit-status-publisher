<%@ page import="jetbrains.buildServer.commitPublisher.perforce.SwarmPublisherSettings" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%--
  ~ Copyright 2000-2022 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

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
    <span class="error" id="error_${pwdField}"></span>
  </td>
</tr>

<tr>
  <th><label for="${createSwarmTestField}">Create Swarm Test</label></th>
  <td>
    <div style="display: flex; gap: 5px; align-items: flex-start;">
      <props:checkboxProperty name="${createSwarmTestField}" style="margin-top: 5px"/>
      <span class="error" id="error_${createSwarmTestField}"></span>
      <span class="note">
      If set, TeamCity will create a test run on the Swarm server and update its status according to
                the build status in TeamCity. Requires an admin-level access for the credentials above.
    </span>
    </div>
  </td>
</tr>

<script>
  $j(document).ready(function() {
    PublisherFeature.showTestConnection("Successfully authenticated at the Perforce Swarm server.");
  });
</script>


