<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<table style="width: 100%">
    <tr>
        <th><label for="${keys.upsourceServerUrl}">Upsource url: <l:star/></label></th>
        <td>
            <props:textProperty name="${keys.upsourceServerUrl}" style="width:18em;"/>
            <span class="error" id="error_${keys.upsourceServerUrl}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.upsourceProjectId}">Upsource project: <l:star/></label></th>
        <td>
            <props:textProperty name="${keys.upsourceProjectId}" style="width:18em;"/>
            <span class="error" id="error_${keys.upsourceProjectId}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.upsourceUsername}">Username: <l:star/></label></th>
        <td>
            <props:textProperty name="${keys.upsourceUsername}" style="width:18em;"/>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.upsourcePassword}">Password: <l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.upsourcePassword}" style="width:18em;"/>
        </td>
    </tr>
</table>

