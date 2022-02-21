<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%--
  ~ Copyright 2000-2022 JetBrains s.r.o.
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

<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
    <tr>
        <th><label for="${keys.giteaServer}">Gitea URL:<l:star/></label></th>
        <td>
            <props:textProperty name="${keys.giteaServer}" className="longField"/>
            <span class="smallNote">
                Format: <strong>http[s]://&lt;hostname&gt;[:&lt;port&gt;]/api/v1</strong>
            </span>
            <span class="error" id="error_${keys.giteaServer}"></span>
        </td>
    </tr>

    <tr>
        <th><label for="${keys.giteaToken}">Private Token:<l:star/></label></th>
        <td>
            <props:passwordProperty name="${keys.giteaToken}" className="mediumField"/>
            <span class="smallNote">
                Can be found at <strong>/user/settings/applications</strong> in Gitea
            </span>
            <span class="error" id="error_${keys.giteaToken}"></span>
            <c:if test="${testConnectionSupported}">
                <script>
                    $j(document).ready(function() {
                        PublisherFeature.showTestConnection();
                    });
                </script>
            </c:if>
        </td>
    </tr>