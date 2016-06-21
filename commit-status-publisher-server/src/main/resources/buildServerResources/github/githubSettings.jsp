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
        <th><label for="${keys.userNameKey}">Username: <l:star/></label></th>
        <td>
          <props:textProperty name="${keys.userNameKey}" className="longField"/>
          <span class="error" id="error_${keys.userNameKey}"></span>
          <span class="smallNote">Specify GitHub user name</span>
        </td>
      </tr>
      <tr>
        <th><label for="${keys.passwordKey}">Password: <l:star/></label></th>
        <td>
          <props:passwordProperty name="${keys.passwordKey}" className="longField"/>
          <span class="error" id="error_${keys.passwordKey}"></span>
          <span class="smallNote">Specify GitHub password</span>
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
            Specify a GitHub <a href="https://github.com/settings/applications" target="_blank">Personal Access Token</a>
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
    <th><label for="${keys.serverKey}">URL: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.serverKey}" className="longField"/>
      <span class="error" id="error_${keys.serverKey}"></span>
    <span class="smallNote">
      Specify GitHub URL. Use <strong>http(s)://[hostname]/api/v3</strong>
      for <a href="https://support.enterprise.github.com/entries/21391237-Using-the-API" target="_blank">GitHub Enterprise</a>
    </span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.failOnSizeKey}">Fail on size: </label></th>
    <td>
      <props:checkboxProperty name="${keys.failOnSizeKey}"/>
    <span class="smallNote">
      Mark this build as a failure in GitHub based on a size too big error
    </span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.bytesKey}">Max Bytes Change: </label></th>
    <td>
      <props:textProperty name="${keys.bytesKey}" className="longField"/>
    <span class="smallNote">
      Maximum size increase in bytes before a warning shows up. Blank is disabled
    </span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.pctKey}">Max Percent Change: </label></th>
    <td>
      <props:textProperty name="${keys.pctKey}" className="longField"/>
    <span class="smallNote">
      Maximum size in percent increase before a warning shows up. Blank is disabled
    </span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.artifactsKey}">Artifacts:</label></th>
    <td>
      <props:multilineProperty name="${keys.artifactsKey}" linkTitle="Artifacts" cols="47" rows="3"/>
      <span class="error" id="error_${keys.artifactsKey}"></span>
    <span class="smallNote">
      Specify a <strong> UNIQUE </strong> match for each file you wish to track size
    </span>
    </td>
  </tr>

</table>