package org.sakaiproject.gradebookng.tool.panels;

import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.sakaiproject.gradebookng.tool.component.GbAjaxLink;
import org.sakaiproject.gradebookng.tool.model.GbModalWindow;
import org.sakaiproject.gradebookng.tool.model.GradebookUiSettings;
import org.sakaiproject.gradebookng.tool.pages.GradebookPage;

/**
 *
 * Cell panel for the student extra info
 *
 */
public class StudentExtraInfoCellPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	IModel<Map<String, Object>> model;

	public StudentExtraInfoCellPanel(final String id, final IModel<Map<String, Object>> model) {
		super(id, model);
		this.model = model;
	}

	@Override
	public void onInitialize() {
		super.onInitialize();

		// unpack model
		final Map<String, Object> modelData = this.model.getObject();
		final String eid = (String) modelData.get("eid");
		final String firstName = (String) modelData.get("firstName");
		final String lastName = (String) modelData.get("lastName");
		final String displayName = (String) modelData.get("displayName");
		// link
		final GbAjaxLink<String> link = new GbAjaxLink<String>("link") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(final AjaxRequestTarget target) {

				final GradebookPage gradebookPage = (GradebookPage) getPage();
				final GbModalWindow window = gradebookPage.getStudentGradeSummaryWindow();
				final GradebookUiSettings settings = gradebookPage.getUiSettings();

				final Map<String, Object> windowModel = new HashMap<>(StudentExtraInfoCellPanel.this.model.getObject());
				windowModel.put("groupedByCategoryByDefault", settings.isCategoriesEnabled());

				final Component content = new StudentGradeSummaryPanel(window.getContentId(), Model.ofMap(windowModel), window);

				if (window.isShown() && window.isVisible()) {
					window.replace(content);
					content.setVisible(true);
					target.add(content);
				} else {
					window.setContent(content);
					window.setComponentToReturnFocusTo(this);
					window.show(target);
				}

				content.setOutputMarkupId(true);
				final String modalTitle = (new StringResourceModel("heading.studentsummary",
						null, new Object[] { displayName, eid })).getString();
			}
		};
		link.setOutputMarkupId(true);

		// name label
		link.add(new Label("name", firstName));

		// eid label, configurable
		link.add(new Label("eid", eid) {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean isVisible() {
				return true; // TODO use config, will need to be passed in the model map
			}

		});

		add(link);
	}

}
