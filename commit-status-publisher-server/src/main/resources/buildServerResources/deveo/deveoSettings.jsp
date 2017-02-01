<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<jsp:useBean id="keys" class="jetbrains.buildServer.commitPublisher.Constants"/>
<table style="width: 100%">

  <tr>
      <th><label for="${keys.deveoApiHostname}">Deveo API hostname: <l:star/></label></th>
      <td>
        <props:textProperty name="${keys.deveoApiHostname}" style="width:18em;"/>
        <span class="error" id="error_${keys.deveoApiHostname}"></span>
      </td>
   </tr>

  <tr>
    <th><label for="${keys.deveoCompanyKey}">Deveo Company Key: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.deveoCompanyKey}" style="width:18em;"/>
      <span class="error" id="error_${keys.deveoCompanyKey}"></span>
    </td>
  </tr>

   <tr>
    <th><label for="${keys.deveoAccountKey}">Deveo Account Key: <l:star/></label></th>
    <td>
       <props:textProperty name="${keys.deveoAccountKey}" style="width:18em;"/>
       <span class="error" id="error_${keys.deveoAccountKey}"></span>
     </td>
   </tr>

  <tr>
    <th><label for="${keys.deveoPluginKey}">Deveo Plugin Key: <l:star/></label></th>
    <td>
      <props:textProperty name="${keys.deveoPluginKey}" style="width:18em;"/>
      <span class="error" id="error_${keys.deveoPluginKey}"></span>
    </td>
  </tr>

</table>

