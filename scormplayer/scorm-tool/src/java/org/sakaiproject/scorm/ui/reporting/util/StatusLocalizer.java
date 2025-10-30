package org.sakaiproject.scorm.ui.reporting.util;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.sakaiproject.util.ResourceLoader;

/**
 * Utility for localizing SCORM completion and success status codes through Wicket's resource bundles.
 */
public final class StatusLocalizer
{
	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
	private static final String BUNDLE = StatusLocalizer.class.getName();

	private StatusLocalizer()
	{
	}

	public static String completionStatus(Component component, String status)
	{
		return localize(resolveLocale(component), status, "completion.status.");
	}

	public static String successStatus(Component component, String status)
	{
		return localize(resolveLocale(component), status, "success.status.");
	}

	private static Locale resolveLocale(Component component)
	{
		if (component != null)
		{
			return component.getLocale();
		}

		if (Session.exists())
		{
			return Session.get().getLocale();
		}

		return Locale.getDefault();
	}

	private static String localize(Locale locale, String status, String prefix)
	{
		if (StringUtils.isBlank(status))
		{
			return StringUtils.defaultString(status);
		}

		String normalized = NON_ALNUM.matcher(status.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
		normalized = StringUtils.strip(normalized, "-");

		String key = prefix + normalized;
		ResourceLoader loader = new ResourceLoader(BUNDLE);
		loader.setContextLocale(locale);
		String value = loader.getString(key, status);
		return StringUtils.defaultIfBlank(value, status);
	}
}
