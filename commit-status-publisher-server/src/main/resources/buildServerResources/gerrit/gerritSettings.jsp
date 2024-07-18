<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>


<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.gerrit.GerritConstants"/>

  <tr>
    <th><label for="${keys.server}">Gerrit Server:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.server}" className="longField"/>
      <span class="smallNote">Format: <strong>&lt;host&gt;[:&lt;port&gt;]</strong></span>
      <span class="error" id="error_${keys.server}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.username}">Gerrit Username:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.username}" className="longField"/>
      <span class="error" id="error_${keys.username}"></span>
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
    <th><label for="${keys.project}">Gerrit Project Name:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.project}" className="longField"/>
      <span class="error" id="error_${keys.project}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.label}">Gerrit Label:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.label}" className="longField" />
      <span class="error" id="error_${keys.label}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.successVote}">Successful Build Vote:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.successVote}" className="longField"/>
      <span class="error" id="error_${keys.successVote}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.failureVote}">Failed Build Vote:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.failureVote}" className="longField"/>
      <span class="error" id="error_${keys.failureVote}"></span>
      <c:if test="${testConnectionSupported}">
        <script>
          $j(document).ready(function() {
            PublisherFeature.showTestConnection();
          });
        </script>
      </c:if>
    </td>
  </tr>