<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
    <tr>
        <th><label for="${keys.gitlabServer}">GitLab URL: <l:star/></label></th>
        <td>
            <props:textProperty name="${keys.gitlabServer}" className="longField"/>
            <span class="smallNote">
                Format: <strong>http[s]://&lt;hostname&gt;[:&lt;port&gt;]/api/v4</strong>
            </span>
            <span class="error" id="error_${keys.gitlabServer}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.gitlabToken}">Private Token: <l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.gitlabToken}" className="mediumField"/>
            <span class="smallNote">
                Can be found at <strong>/profile/account</strong> in GitLab
            </span>
            <span class="error" id="error_${keys.gitlabToken}"></span>
            <c:if test="${testConnectionSupported}">
                <script>
                    $j(document).ready(function() {
                        PublisherFeature.showTestConnection();
                    });
                </script>
            </c:if>
        </td>
    </tr>