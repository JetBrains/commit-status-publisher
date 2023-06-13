<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
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

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="constants" class="jetbrains.buildServer.commitPublisher.Constants" scope="request" />
<jsp:useBean id="buildForm" type="jetbrains.buildServer.controllers.admin.projects.EditableBuildTypeSettingsForm" scope="request"/>
<c:url value="${publisherSettingsUrl}" var="settingsUrl"/>

<script type="text/javascript">
  var testConnectionSuccessInfo = "";

  PublisherFeature = OO.extend(BS.BuildFeatureDialog, {

    vcsRoots: new Map(),

    showTestConnection: function() {
      testConnectionSuccessInfo = "";
      $j("#testConnectionButton").show()
    },
    showTestConnection: function(successInfo) {
      testConnectionSuccessInfo = successInfo;
      $j("#testConnectionButton").show()
    },
    hideTestConnection: function() {
      $j("#testConnectionButton").hide()
    },
    showPublisherSettings: function() {
      PublisherFeature.hideTestConnection();
      var url = '${settingsUrl}?${constants.publisherIdParam}=' + $('${constants.publisherIdParam}').value  + "&projectId=${projectId}";
      $j('#publisherSettingsProgress').show();
      $j.get(url, function(xhr) {
        $j('#publisherSettingsProgress').hide();
        $j("#publisherProperties").html(xhr);
        BS.AvailableParams.attachPopups('settingsId=${buildForm.settingsId}', 'textProperty', 'multilineProperty');
      });
      return false;
    },

    testConnection: function() {
      var that = this;
      var info = "";
      var success = true;
      var url = '${settingsUrl}?${constants.publisherIdParam}=' + $('${constants.publisherIdParam}').value  + "&projectId=${projectId}&testconnection=yes";
      BS.PasswordFormSaver.save(that, url, OO.extend(BS.ErrorsAwareListener, {
        onBeginSave: function(form) {
          form.setSaving(true);
          form.disable();
        },

        onTestConnectionFailedError: function(elem) {
          if (success) {
            info = "";
          } else if ("" != info) {
            info += "\n";
          }
          info += elem.textContent || elem.text;
          success = false;
        },

        onCompleteSave: function (form, responseXML, err) {
          BS.XMLResponse.processErrors(responseXML, that, null);
          let successInfo = "";
          let preserveHtml = false;
          if (testConnectionSuccessInfo) {
            if (testConnectionSuccessInfo instanceof Function) {
              const info = testConnectionSuccessInfo.call();
              if (typeof info === 'string') {
                successInfo = info;
                preserveHtml = false;
              }
              else {
                successInfo = info.text;
                preserveHtml = info.preserveHtml;
              }
            } else {
              successInfo = testConnectionSuccessInfo;
            }
          }
          BS.TestConnectionDialog.show(success, success ? successInfo : info, null, preserveHtml);
          form.setSaving(false);
          form.enable();
        }
      }));
    }
  });
</script>
<span id="buildFeatureTestConnectionButton" style="display:none;">
  <forms:submit id="testConnectionButton" type="button" label="Test connection" onclick="PublisherFeature.testConnection();"/>
</span>
<script>
  $j(document).ready(function() {
    placeholder = $j("span#editBuildFeatureAdditionalButtons");
    if(placeholder.length) {
      placeholder.empty();
      placeholder.append($j("span#buildFeatureTestConnectionButton *"));
    }
    PublisherFeature.hideTestConnection();
  });
</script>
  <tr>
    <th><label for="${constants.vcsRootIdParam}">VCS Root:<l:star/></label></th>
    <td>
      <props:selectProperty name="${constants.vcsRootIdParam}" className="longField" enableFilter="true" style="width: 95%;">
        <props:option value="">&lt;All attached VCS Roots&gt;</props:option>
        <c:forEach var="vcsRoot" items="${vcsRoots}">
          <props:option value="${vcsRoot.externalId}"><c:out value="${vcsRoot.name}"/></props:option>
          <script type="text/javascript">
            PublisherFeature.vcsRoots.set('${vcsRoot.externalId}', {
              name: '${vcsRoot.name}',
              url: '${vcsRoot.getProperty('url')}'
            });
          </script>
        </c:forEach>
      </props:selectProperty>
      <c:if test="${hasMissingVcsRoot}">
        <span class="error">
          VCS root
          <c:if test="${not empty missingVcsRoot}">
            <admin:editVcsRootLink vcsRoot="${missingVcsRoot}" editingScope="" cameFromUrl="${pageUrl}">
              <c:out value="${missingVcsRoot.name}" />
            </admin:editVcsRootLink>
          </c:if>
          used by the build feature is not attached to the configuration.
        </span>
      </c:if>
      <span class="error" id="error_${constants.vcsRootIdParam}"></span>
      <span class="smallNote">Choose a repository to use for publishing a build status. Choose <strong>&lt;All attached VCS roots&gt;</strong> option if you want Commit Status Publisher to attempt
        publishing statuses for commits in all attached VCS roots.</span>
    </td>
  </tr>
  <tr>
    <th>
      <label for="${constants.publisherIdParam}">Publisher:<l:star/></label>
    </th>
    <td>
      <props:selectProperty name="${constants.publisherIdParam}" onchange="PublisherFeature.showPublisherSettings()" enableFilter="true" className="mediumField">
        <c:forEach var="publisher" items="${publishers}">
          <props:option value="${publisher.id}"><c:out value="${publisher.name}"/></props:option>
        </c:forEach>
      </props:selectProperty> <forms:progressRing id="publisherSettingsProgress" style="float:none; display: none;"/>
      <span class="error" id="error_${constants.publisherIdParam}"></span>
    </td>
  </tr>
<bs:dialog dialogId="testConnectionDialog" title="Test Connection" closeCommand="BS.TestConnectionDialog.close();"
           closeAttrs="showdiscardchangesmessage='false'">
  <div id="testConnectionStatus"></div>
  <div id="testConnectionDetails" class="mono"></div>
</bs:dialog>
