<%-- $Id: $
include file for displaying fill in the numeric questions
--%>
<!--
<%--
***********************************************************************************
*
* Copyright (c) 2004, 2005, 2006 The Sakai Foundation.
*
* Licensed under the Educational Community License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.osedu.org/licenses/ECL-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License. 
*
**********************************************************************************/
--%>
-->

<script src="/library/webjars/jquery/1.12.4/jquery.min.js"></script>
<script src="/samigo-app/js/selection.author.preview.js"></script>
<script src="/samigo-app/js/selection.student.preview.js"></script>
<link rel="stylesheet" type="text/css" href="/samigo-app/css/imageQuestion.author.css">
<link rel="stylesheet" type="text/css" href="/samigo-app/css/imageQuestion.student.css">

<f:verbatim>
<script src="/samigo-app/js/image-map-evaluation.js"></script>
</f:verbatim>

<h:outputText value="#{question.text}"  escape="false"/>

<h:dataTable value="#{question.itemTextArraySorted}" var="itemText">
 <h:column>
   <h:outputText value="#{itemText.sequence}#{evaluationMessages.dot} #{itemText.text}" escape="false" />
   <h:dataTable value="#{itemText.answerArraySorted}" var="answer">     
     <h:column> 
	   <h:outputText escape="false" value="<input type='hidden' id='hiddenSerializedCoords_#{question.sequence}_#{itemText.sequence}' value='#{answer.text}' />" /> 
     </h:column>
   </h:dataTable>
 </h:column>
</h:dataTable>

<div id="imageMapContainer" class='authorImageContainer'>
    <img id='img' src='<h:outputText value="#{question.imageMapSrc}"/>' alt='<h:outputText value="#{question.imageMapAltText}"/>'/>
</div>
<%@ include file="/jsf/evaluation/item/displayTags.jsp" %>
