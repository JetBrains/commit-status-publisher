<%--
  ~ Copyright 2000-2024 JetBrains s.r.o.
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

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>


<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>

  <tr>
    <th><label for="${keys.gerritServer}">Gerrit Server:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritServer}" className="longField"/>
      <span class="smallNote">Format: <strong>&lt;host&gt;[:&lt;port&gt;]</strong></span>
      <span class="error" id="error_${keys.gerritServer}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritUsername}">Gerrit Username:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritUsername}" className="longField"/>
      <span class="error" id="error_${keys.gerritUsername}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.sshKey}">SSH Key:<l:star/></label></th>
    <td>
      <admin:sshKeys projectId="${projectId}"/>
      <span class="error" id="error_${keys.sshKey}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritProject}">Gerrit Project Name:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritProject}" className="longField"/>
      <span class="error" id="error_${keys.gerritProject}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritLabel}">Gerrit Label:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritLabel}" className="longField" />
      <span class="error" id="error_${keys.gerritLabel}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritSuccessVote}">Successful Build Vote:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritSuccessVote}" className="longField"/>
      <span class="error" id="error_${keys.gerritSuccessVote}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.gerritFailureVote}">Failed Build Vote:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.gerritFailureVote}" className="longField"/>
      <span class="error" id="error_${keys.gerritFailureVote}"></span>
      <c:if test="${testConnectionSupported}">
        <script>
          $j(document).ready(function() {
            PublisherFeature.showTestConnection();
          });
        </script>
      </c:if>
    </td>
  </tr>