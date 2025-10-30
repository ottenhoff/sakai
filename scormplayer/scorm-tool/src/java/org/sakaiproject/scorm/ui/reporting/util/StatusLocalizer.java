package org.sakaiproject.scorm.ui.reporting.util;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.Localizer;

/**
 * Utility for localizing SCORM completion and success status codes through Wicket's resource bundles.
 */
public final class StatusLocalizer
{
	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

	private StatusLocalizer()
	{
	}

	public static String completionStatus(Component component, String status)
	{
		return localize(component, status, "completion.status.");
	}

	public static String successStatus(Component component, String status)
	{
		return localize(component, status, "success.status.");
	}

	private static String localize(Component component, String status, String prefix)
	{
		if (StringUtils.isBlank(status) || component == null)
		{
			return StringUtils.defaultString(status);
		}

		String normalized = NON_ALNUM.matcher(status.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
		normalized = StringUtils.strip(normalized, "-");

		String key = prefix + normalized;
		Localizer localizer = component.getLocalizer();
		if (localizer != null)
		{
			return localizer.getString(key, component, status);
		}

		return status;
	}
}
