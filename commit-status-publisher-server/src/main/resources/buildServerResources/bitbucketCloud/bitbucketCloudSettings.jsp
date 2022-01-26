<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
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
    <th><label for="${keys.bitbucketCloudUsername}">Bitbucket Username:<l:star/></label></th>
    <td>
      <props:textProperty name="${keys.bitbucketCloudUsername}" className="mediumField"/>
      <span class="error" id="error_${keys.bitbucketCloudUsername}"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${keys.bitbucketCloudPassword}">Bitbucket Password:<l:star/></label></th>
    <td>
      <props:passwordProperty name="${keys.bitbucketCloudPassword}" className="mediumField"/>
      <span class="error" id="error_${keys.bitbucketCloudPassword}"></span>
      <c:if test="${testConnectionSupported}">
        <script>
          $j(document).ready(function() {
            PublisherFeature.showTestConnection("This ensures that the repository is reachable under the provided credentials.\nIf status publishing still fails, it can be due to insufficient permissions of the corresponding BitBucket Cloud user.");
          });
        </script>
      </c:if>
    </td>
  </tr>

