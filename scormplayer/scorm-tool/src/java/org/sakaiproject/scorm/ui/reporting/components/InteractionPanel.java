/**
 * Copyright (c) 2007 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.scorm.ui.reporting.components;

import java.text.DateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;

import org.sakaiproject.scorm.model.api.Interaction;
import org.sakaiproject.scorm.ui.Icon;
import org.sakaiproject.scorm.ui.reporting.util.ScormDurationFormatter;
import org.sakaiproject.time.api.UserTimeService;

public class InteractionPanel extends Panel
{
	private static final long serialVersionUID = 1L;

	private static ResourceReference BLANK_ICON = new PackageResourceReference(InteractionPanel.class, "res/brick.png");
	private static ResourceReference CORRECT_ICON = new PackageResourceReference(InteractionPanel.class, "res/tick.png");
	private static ResourceReference INCORRECT_ICON = new PackageResourceReference(InteractionPanel.class, "res/cross.png");
	private static ResourceReference UNANTICIPATED_ICON = new PackageResourceReference(InteractionPanel.class, "res/exclamation.png");
	private static ResourceReference NEUTRAL_ICON = new PackageResourceReference(InteractionPanel.class, "res/page_white_text.png");

	@SpringBean(name = "org.sakaiproject.time.api.UserTimeService")
	private transient UserTimeService userTimeService;

	public InteractionPanel(String id, Interaction interaction)
	{
		super(id, new CompoundPropertyModel(interaction));

		add(new Label("interactionId"));
		add(new Label("type", new ResourceModel(new StringBuilder("type.").append(interaction.getType()).toString())));
		add(new Label("description"));
		add(new Label("learnerResponse"));

		StringBuilder correctResponse = new StringBuilder();
		for (String response : interaction.getCorrectResponses())
		{
			if (response != null)
			{
				correctResponse.append(response);
			}
		}

		Label correctResponseLabel = new Label("correctResponse", correctResponse.toString());
		add(correctResponseLabel);
		add(new Label("weighting", String.valueOf(interaction.getWeighting())));
		String formattedTimestamp = formatTimestamp(interaction.getTimestamp());
		add(new Label("timestamp", Model.of(formattedTimestamp)));
		String formattedLatency = ScormDurationFormatter.formatOrNull(interaction.getLatency(), getLocale());
		add(new Label("latency", Model.of(formattedLatency != null ? formattedLatency : "")));

		String result = interaction.getResult();
		String normalizedResult = (result != null) ? result.toLowerCase(Locale.ROOT) : null;

		if (normalizedResult != null)
		{
			String resourceKey = new StringBuilder("result.").append(normalizedResult).toString();
			add(new Label("result", new ResourceModel(resourceKey, result)));
		}
		else
		{
			add(new Label("result", Model.of("")));
		}

		boolean showIcon = true;
		boolean isNeutral = false;
		ResourceReference resultIconReference = BLANK_ICON;
		if (normalizedResult != null)
		{
			if ("correct".equals(normalizedResult))
			{
				resultIconReference = CORRECT_ICON;
			}
			else if ("incorrect".equals(normalizedResult))
			{
				resultIconReference = INCORRECT_ICON;
			}
			else if ("neutral".equals(normalizedResult))
			{
				resultIconReference = NEUTRAL_ICON;
				isNeutral = true;
			}
			else if ("unanticipated".equals(normalizedResult))
			{
				resultIconReference = UNANTICIPATED_ICON;
			}
			else
			{
				showIcon = false;
			}
		}
		else
		{
			showIcon = false;
		}

		Icon resultIcon = new Icon("resultIcon", resultIconReference);
		resultIcon.setVisible(showIcon);
		add(resultIcon);

		correctResponseLabel.setVisible(! isNeutral);
	}

	private String formatTimestamp(String timestamp)
	{
		if (StringUtils.isBlank(timestamp))
		{
			return "";
		}

		try
		{
			OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			Instant instant = offsetDateTime.toInstant();
			return userTimeService.dateTimeFormat(Date.from(instant), getLocale(), DateFormat.MEDIUM);
		}
		catch (DateTimeParseException e)
		{
			throw new IllegalArgumentException("Unable to parse SCORM timestamp: " + timestamp, e);
		}
	}
}
