<%@ page import="java.util.*, javax.faces.context.*, javax.faces.application.*,
                 javax.faces.el.*, org.sakaiproject.tool.messageforums.*,
                 org.sakaiproject.api.app.messageforums.*,
                 org.sakaiproject.site.cover.SiteService,
                 org.sakaiproject.tool.cover.ToolManager" %>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f" %>
<%@ taglib uri="http://sakaiproject.org/jsf2/sakai" prefix="sakai" %>
<%@ taglib uri="http://sakaiproject.org/jsf/messageforums" prefix="mf" %>
<jsp:useBean id="msgs" class="org.sakaiproject.util.ResourceLoader" scope="session">
   <jsp:setProperty name="msgs" property="baseName" value="org.sakaiproject.api.app.messagecenter.bundle.Messages"/>
</jsp:useBean>

<f:view>
    <sakai:view toolCssHref="/messageforums-tool/css/msgcntr.css">
        <script>includeLatestJQuery("msgcntr");</script>
        <script src="/messageforums-tool/js/sak-10625.js"></script>
        <script src="/messageforums-tool/js/messages.js"></script>
        <script src="/messageforums-tool/js/forum.js"></script>
        <script>includeWebjarLibrary('ckeditor4')</script>
        <script>includeWebjarLibrary('awesomplete')</script>
        <script>includeWebjarLibrary('bootstrap')</script>
        <script src="/library/js/sakai-reminder.js"></script>
        <script type="module" src="/webcomponents/bundles/rubric-association-requirements.js<h:outputText value="#{ForumTool.CDNQuery}" />"></script>
        <script type="module" src="/vuecomponents/js/sakai.min.js<h:outputText value="#{ForumTool.CDNQuery}" />"></script>
        <h:form id="msgForum">
            <!--jsp\discussionForum\message\dfMsgGrade.jsp-->

            <%
            FacesContext context = FacesContext.getCurrentInstance();
            Application app = context.getApplication();
            ValueBinding binding = app.createValueBinding("#{ForumTool}");
            DiscussionForumTool forumTool = (DiscussionForumTool) binding.getValue(context);

            //Check if user called this page with a popup dialog

            String messageId = request.getParameter("messageId");
            String topicId = request.getParameter("topicId");
            String forumId = request.getParameter("forumId");
            String userId = request.getParameter("userId");
            String frameId = request.getParameter("frameId");
            String dialogDivId = request.getParameter("dialogDivId");
            String gradesSavedDiv = request.getParameter("gradesSavedDiv");

            boolean isDialogBox = false;
            if (userId != null && !"".equals(userId) && forumId != null && !"".equals(forumId)) {
                //message info was passed via parameters...
                //set up this information in DiscussionForumTool
                //All permission will be hanlded in "rendered" fields in this page below

                isDialogBox = true;

                String noProcessing = request.getParameter("noProccessing");
                if(noProcessing == null || "". equals(noProcessing)){
                    //this will set all the variables that need to be set
                    String result = forumTool.processDfMsgGrdFromThread(messageId, topicId, forumId, userId);
                }
            %>
            <script>
                parent.dialogutil.replaceBodyOnLoad("myLoaded();", this);
                function myLoaded() {
                    resetHeight();
                    //don't want to update the parent's height cause that'll jack up the sizing we've already done.
                 }
            </script>
            <%
            }

            String entityId = forumTool.getRubricAssociationId();

            if (userId == null) userId = forumTool.getUserId();

            String rbcsEvaluationId = entityId + "." + userId;
            String rbcsEvaluationOwnerId = userId;
            %>

            <script>
                function closeDialogBoxIfExists() {
                    //if isDialogBox, there will be javascript that is ran, otherwise its an empty function
                    <% if (isDialogBox) { %>
                        parent.dialogutil.closeDialog('<%=dialogDivId%>','<%=frameId%>');
                    <% }%>
                }
                <%

                if (forumTool.isDialogGradeSavedSuccessfully()) {
                    forumTool.setDialogGradeSavedSuccessfully(false);
                %>
                    parent.dialogutil.showDiv('<%=gradesSavedDiv%>');
                    closeDialogBoxIfExists();
                    <%
                }
                %>
            </script>
            <script src="/library/js/spinner.js"></script>

            <script>
                var isGradebookGroupEnabled = <h:outputText value="#{ForumTool.gradebookGroupEnabled}"/>;
                
                $(document).ready(function() {
                    if (isGradebookGroupEnabled) {
                        window.syncGbSelectorInput("gb-selector", "msgForum:gb_selector");
                    }

                    try {
                        var sakaiReminder = new SakaiReminder();
                        new Awesomplete($('.awesomplete')[0], {
                            list: sakaiReminder.getAll()
                        });
                        $('#msgForum').submit(function (e) {
                            $('textarea.awesomplete').each(function () {
                                sakaiReminder.new($(this).val());
                            });
                        });
                    } catch (err) {
                        //Just ignore the exception, happens when a gradebook item is not selected.
                    }
                });
            </script>

            <span class="close-button fa fa-times" onClick="SPNR.disableControlsAndSpin(this, null);closeDialogBoxIfExists();" aria-label="<h:outputText value="#{msgs.close_window}" />"></span>
            <h3><h:outputText value="#{msgs.cdfm_grade_msg}" /></h3>
            <h4>
                <h:outputText value="#{ForumTool.selectedForum.forum.title}" />
                <h:outputText value=" #{msgs.cdfm_dash} " rendered="#{!empty ForumTool.selectedTopic}"/>
                <h:outputText   value="#{ForumTool.selectedTopic.topic.title}" />
            </h4>
            <h:messages globalOnly="true" infoClass="success" errorClass="alertMessage" rendered="#{! empty facesContext.maximumSeverity}"/>
            <h:panelGroup rendered="#{ForumTool.selectedMessage != null}">
                <f:verbatim>
                <div class="singleMessage">
                </f:verbatim>
                    <h:outputText value="#{ForumTool.selectedMessage.message.title} " styleClass="title"/>
                    <h:outputText value="#{ForumTool.selectedMessage.anonAwareAuthor}" styleClass="#{ForumTool.selectedMessage.useAnonymousId ? 'anonymousAuthor' : ''}" />
                    <h:outputText value=" #{msgs.cdfm_me}" rendered="#{ForumTool.selectedMessage.currentUserAndAnonymous}" />
                    <h:outputText value=" #{msgs.cdfm_openb} " />
                    <h:outputText value="#{ForumTool.selectedMessage.message.created}">
                        <f:convertDateTime pattern="#{msgs.date_format}" timeZone="#{ForumTool.userTimeZone}" locale="#{ForumTool.userLocale}"/>
                    </h:outputText>
                    <h:outputText value=" #{msgs.cdfm_closeb}" />
                    <%-- Attachments --%>
                    <h:dataTable value="#{ForumTool.selectedMessage.attachList}"    var="eachAttach" rendered="#{!empty ForumTool.selectedMessage.attachList}">
                        <h:column rendered="#{!empty ForumTool.selectedMessage.message.attachments}">
                            <sakai:contentTypeMap fileType="#{eachAttach.attachment.attachmentType}" mapType="image" var="imagePath" pathPrefix="/library/image/"/>
                            <h:graphicImage id="exampleFileIcon" value="#{imagePath}" />
                            <h:outputLink value="#{eachAttach.url}" target="_new_window">
                                <h:outputText value="#{eachAttach.attachment.attachmentName}" />
                            </h:outputLink>
                        </h:column>
                    </h:dataTable>
                    <h:outputText escape="false" value="#{ForumTool.selectedMessage.message.body}"  style="display:block;margin-top:2em" styleClass="textPanel"/>
                <f:verbatim>
                </div>
                </f:verbatim>
            </h:panelGroup>

            <h:panelGroup styleClass="instruction" rendered="#{ForumTool.allowedToGradeItem}">
                <h:outputText value="#{msgs.cdfm_required}" />
                <h:outputText value="#{msgs.pvt_star}" styleClass="reqStarInline"/>
            </h:panelGroup>
            <h:panelGroup layout="block" styleClass="row" rendered="#{ForumTool.gradebookGroupEnabled}">
                <sakai-multi-gradebook
                        id="gb-selector"
                        app-name="sakai.forums"
                        site-id='<h:outputText value="#{ForumTool.siteId}" />'
                        user-id='<h:outputText value="#{ForumTool.selectedGradedUserId}" />'
                        group-id='<h:outputText value="#{ForumTool.groupId}" />'
                        selected-temp='<h:outputText value="#{ForumTool.selectedAssign}" />'
                </sakai-multi-gradebook>
                <h:inputHidden id="gb_selector" value="#{ForumTool.currentChange}" />
            </h:panelGroup>
            <h:panelGrid id="grade-message-options" styleClass="jsfFormTable" columns="1" columnClasses="shorttext spinnerBesideContainer" border="0">
                <h:panelGroup rendered="#{!hasAssociatedRubric && !ForumTool.gradebookGroupEnabled}">
                    <h:outputLabel for="assignment"  rendered="#{ForumTool.allowedToGradeItem}">
                        <h:outputText value="#{msgs.cdfm_info_required_sign}" styleClass="reqStarInline" style="padding-right:3px"/>
                        <h:outputText  value="#{msgs.cdfm_assignments}"/>
                    </h:outputLabel>
                    <h:selectOneMenu id="assignment" value="#{ForumTool.selectedAssign}" valueChangeListener="#{ForumTool.processGradeAssignChange}"
                        onchange="SPNR.insertSpinnerAfter( this, null, null );document.forms[0].submit();">
                        <f:selectItems value="#{ForumTool.assignments}" />
                    </h:selectOneMenu>
                    <h:outputFormat value=" #{msgs.cdfm_points_possible}" rendered="#{!ForumTool.selGBItemRestricted && ForumTool.gbItemPointsPossible != null && ForumTool.gradeByPoints}">
                        <f:param value="#{ForumTool.gbItemPointsPossible}"/>
                    </h:outputFormat>
                </h:panelGroup>
                <h:outputText value="" rendered="#{ForumTool.selGBItemRestricted && !ForumTool.noItemSelected}" />
                <h:outputText value="#{msgs.cdfm_no_gb_perm}" rendered="#{ForumTool.selGBItemRestricted && !ForumTool.noItemSelected}" styleClass="alertMessage"/>
                <h:panelGroup  rendered="#{!ForumTool.selGBItemRestricted}">
                    <h:outputLabel for="dfMsgGradeGradePoint" rendered="#{ForumTool.allowedToGradeItem}" >
                        <h:outputText value="#{msgs.cdfm_info_required_sign}" styleClass="reqStarInline"  style="padding-right:3px" />
                        <h:outputText  value="#{msgs.cdfm_grade_points}" rendered="#{ForumTool.gradeByPoints}"/>
                        <h:outputText  value="#{msgs.cdfm_grade_percent}" rendered="#{ForumTool.gradeByPercent}"/>
                        <h:outputText  value="#{msgs.cdfm_grade_letter}" rendered="#{ForumTool.gradeByLetter}"/>
                    </h:outputLabel>
                    <h:panelGroup>
                        <h:inputText value="#{ForumTool.gradePoint}" id="dfMsgGradeGradePoint" size="5" rendered="#{ForumTool.allowedToGradeItem}" />
                        <h:outputText value="%" rendered="#{ForumTool.gradeByPercent}" />
                        <h:message for="dfMsgGradeGradePoint" styleClass="alertMessage"  rendered="#{ForumTool.allowedToGradeItem}"/>
                    </h:panelGroup>

                    <h:panelGroup rendered="#{!ForumTool.allowedToGradeItem}" >
                        <h:outputText value="#{ForumTool.gradePoint}" rendered="#{ForumTool.gradePoint != null}" />
                        <h:outputText value="#{msgs.cdfm_null_points}" rendered="#{ForumTool.gradePoint == null}" />
                    </h:panelGroup>
                </h:panelGroup>
                <h:panelGroup>
                    <h:outputLabel  for="comments" value="#{msgs.cdfm_comments}" rendered="#{!ForumTool.selGBItemRestricted}"/>
                    <h:inputTextarea id="comments" value="#{ForumTool.gradeComment}" rows="5" cols="50" styleClass="awesomplete"
                        rendered="#{!ForumTool.selGBItemRestricted}" readonly="#{!ForumTool.allowedToGradeItem}"/>
                </h:panelGroup>
            </h:panelGrid>

            <h:panelGroup rendered="#{hasAssociatedRubric}">
                <sakai-rubric-grading
                    tool-id="sakai.gradebookng"
                    site-id='<h:outputText value="#{ForumTool.siteId}" />'
                    entity-id='<%= entityId %>'
                    evaluated-item-id='<%= rbcsEvaluationId %>'
                    evaluated-item-owner-id='<%= rbcsEvaluationOwnerId %>'>
                </sakai-rubric-grading>
            </h:panelGroup>

            <sakai:button_bar>
                <% if(isDialogBox){ %>
                    <h:commandButton id="save" action="#{ForumTool.processDfGradeSubmitFromDialog}" value="#{msgs.cdfm_submit_grade}"
                        accesskey="s" styleClass="active" disabled="#{!ForumTool.allowedToGradeItem}" onclick="SPNR.disableControlsAndSpin( this, null );MFR.saveRubric();" />
                    <h:commandButton id="cancel" action="#{ForumTool.processDfGradeCancelFromDialog}" value="#{msgs.cdfm_cancel}" accesskey="x"
                        onclick="MFR.cancelGrading();" />
                <% }else {%>
                    <h:commandButton action="#{ForumTool.processDfGradeSubmit}" value="#{msgs.cdfm_submit_grade}"
                        accesskey="s" styleClass="active" disabled="#{!ForumTool.allowedToGradeItem}"
                        onclick="SPNR.disableControlsAndSpin( this, null ); MFR.saveRubric();" />
                    <h:commandButton action="#{ForumTool.processDfGradeCancel}" value="#{msgs.cdfm_cancel}" accesskey="x"
                        onclick="MFR.cancelGrading();" />
                <%}%>
                <h:commandButton value="#{msgs.cdfm_send_info}" onclick="document.forms[0].submit();" accesskey="x" action="#{ForumTool.processGradeAssignSend}" />
            </sakai:button_bar>

            <% if(isDialogBox){ %>
            <!-- This is used to keep the dialogbox state when going to the next page (this page) -->
            <f:verbatim>
            <input type="text" id="userId" name="userId" value="<%=userId%>" style="display: none;"/>
            <input type="text" id="messageId" name="messageId" value="<%=messageId%>" style="display: none;"/>
            <input type="text" id="topicId" name="topicId" value="<%=topicId%>" style="display: none;"/>
            <input type="text" id="forumId" name="forumId" value="<%=forumId%>" style="display: none;"/>
            <input type="text" id="frameId" name="frameId" value="<%=frameId%>" style="display: none;"/>
            <input type="text" id="dialogDivId" name="dialogDivId" value="<%=dialogDivId%>" style="display: none;"/>
            <input type="text" id="gradesSavedDiv" name="gradesSavedDiv" value="<%=gradesSavedDiv%>" style="display: none;"/>
            <input type="text" id="noProccessing" name="noProccessing" value="true" style="display: none;"/>
            </f:verbatim>
            <%}%>
        </h:form>
    </sakai:view>
</f:view>