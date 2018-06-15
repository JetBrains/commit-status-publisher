<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<jsp:useBean id="currentUser" type="jetbrains.buildServer.users.SUser" scope="request"/>
    <tr>
        <th><label for="${keys.upsourceServerUrl}">Upsource URL:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.upsourceServerUrl}" className="longField"/> <span id="hubAppsControl"></span>
            <span class="error" id="error_${keys.upsourceServerUrl}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.upsourceUsername}">Username:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.upsourceUsername}" className="mediumField"/>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.upsourcePassword}">Password:<l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.upsourcePassword}" className="mediumField"/>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.upsourceProjectId}">Upsource Project ID:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.upsourceProjectId}" className="mediumField"/> <span id="hubProjectsControl"></span>
            <span class="error" id="error_${keys.upsourceProjectId}"></span>
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
                        $('${keys.upsourceServerUrl}').value = service.homeUrl;
                        $('${keys.upsourceUsername}').value = '<c:out value="${currentUser.username}"/>';
                    });

                    BS.HubServiceProjectsPopup.installControl('hubProjectsControl',
                                                              function() { return 'applicationName:Upsource and homeUrl:"' + $('${keys.upsourceServerUrl}').value + '"'; },
                                                              function(project) { $('${keys.upsourceProjectId}').value = project.key; }
                    );
                }
            </script>
        </td>
    </tr>

