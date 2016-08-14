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
                Format: <strong>http(s)://[hostname:port]/api/v3</strong>.
            </span>
            <span class="error" id="error_${keys.gitlabServer}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.gitlabGroup}">Group: </label></th>
        <td>
            <props:textProperty name="${keys.gitlabGroup}" style="width:18em;"/>
            <span class="smallNote">
                Format: gitlab-org part in https://gitlab.com/gitlab-org/gitlab-ce.<br /> By default it will be taken from VCS settings.
            </span>
            <span class="error" id="error_${keys.gitlabGroup}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.gitlabProject}">Project: </label></th>
        <td>
            <props:textProperty name="${keys.gitlabProject}" style="width:18em;"/>
            <span class="smallNote">
                Format: gitlab-ce part in https://gitlab.com/gitlab-org/gitlab-ce.<br /> By default it will be taken from VCS settings.
            </span>
            <span class="error" id="error_${keys.gitlabProject}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.gitlabToken}">Private Token: <l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.gitlabToken}" style="width:18em;"/>
            <span class="smallNote">
                Can be found at <strong>/profile/account</strong> in GitLab.
            </span>
            <span class="error" id="error_${keys.gitlabToken}"></span>
        </td>
    </tr>
</table>