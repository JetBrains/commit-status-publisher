<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
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

<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.github.ui.UpdateChangesConstants"/>

<table style="width: 100%">
  <props:selectSectionProperty name="${keys.authenticationTypeKey}" title="Authentication Type">

    <props:selectSectionPropertyContent value="${keys.authenticationTypePasswordValue}" caption="Password">
      <tr>
        <th><label for="${keys.userNameKey}">GitHub Username: <l:star/></label></th>
        <td>
          <props:textProperty name="${keys.userNameKey}" className="longField"/>
          <span class="error" id="error_${keys.userNameKey}"></span>
        </td>
      </tr>
      <tr>
        <th><label for="${keys.passwordKey}">GitHub Password: <l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.passwordKey}" className="longField"/>
          <span class="error" id="error_${keys.passwordKey}"></span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>

    <props:selectSectionPropertyContent value="${keys.authenticationTypeTokenValue}" caption="Access Token">
      <tr>
        <th><label for="${keys.accessTokenKey}">Personal Access Token: <l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.accessTokenKey}" className="longField"/>
          <span class="error" id="error_${keys.accessTokenKey}"></span>
          <span class="smallNote">
            GitHub <a href="https://github.com/settings/applications" target="_blank">Personal Access Token</a>
            <br />
            It is required to have the following permissions:
            <strong><em>repo:status</em></strong> and
            <strong><em>public_repo</em></strong> or <strong><em>repo</em></strong> depending on the repository type
          </span>
        </td>
      </tr>
    </props:selectSectionPropertyContent>
  </props:selectSectionProperty>

  <tr>
    <th><label for="${keys.serverKey}">GitHub URL: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.serverKey}" className="longField"/>
      <span class="error" id="error_${keys.serverKey}"></span>
    <span class="smallNote">
      Format <strong>http(s)://[hostname]/api/v3</strong>
      for <a href="https://support.enterprise.github.com/entries/21391237-Using-the-API" target="_blank">GitHub Enterprise</a>
    </span>
    </td>
  </tr>
  <tr>
    <th><label for="${keys.contextKey}">Status Context: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.contextKey}" className="longField"/>
      <span class="error" id="error_${keys.contextKey}"></span>
    <span class="smallNote">
      GitHub status context name. Specifies what service is providing the status updates.
    </span>
    </td>
  </tr>
</table>