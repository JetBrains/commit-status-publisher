<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<table style="width: 100%">
    <tr>
        <th><label for="${keys.gitlabServer}">GitLab URL: <l:star/></label></th>
        <td>
            <props:textProperty name="${keys.gitlabServer}" style="width:18em;"/>
            <span class="smallNote">
                Format: <strong>http(s)://[hostname:port]/api/v3</strong>
            </span>
            <span class="error" id="error_${keys.gitlabServer}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.gitlabToken}">Private Token: <l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.gitlabToken}" style="width:18em;"/>
            <span class="smallNote">
                Can be found at <strong>/profile/account</strong> in GitLab
            </span>
            <span class="error" id="error_${keys.gitlabToken}"></span>
        </td>
    </tr>
</table>