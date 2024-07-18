<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>


<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.upsource.UpsourceConstants"/>
<jsp:useBean id="currentUser" type="jetbrains.buildServer.users.SUser" scope="request"/>
    <tr>
        <th><label for="${keys.serverUrl}">Upsource URL:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.serverUrl}" className="longField"/> <span id="hubAppsControl"></span>
            <span class="error" id="error_${keys.serverUrl}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.username}">Username:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.username}" className="longField"/>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.password}">Password:<l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.password}" className="longField"/>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.projectId}">Upsource Project ID:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.projectId}" className="longField"/> <span id="hubProjectsControl"></span>
            <span class="error" id="error_${keys.projectId}"></span>
            <c:if test="${testConnectionSupported}">
                <script>
                    $j(document).ready(function() {
                        PublisherFeature.showTestConnection();
                    });
                </script>
            </c:if>
            <script type="text/javascript">
                if (BS.HubApplicationsPopup) {
                    BS.HubApplicationsPopup.installControl('hubAppsControl', 'Upsource', function(service) {
                        $('${keys.serverUrl}').value = service.homeUrl;
                        $('${keys.username}').value = '<c:out value="${currentUser.username}"/>';
                    });

                    BS.HubServiceProjectsPopup.installControl('hubProjectsControl',
                                                              function() { return 'applicationName:Upsource and homeUrl:"' + $('${keys.serverUrl}').value + '"'; },
                                                              function(project) { $('${keys.projectId}').value = project.key; }
                    );
                }
            </script>
        </td>
    </tr>