package org.sakaiproject.scorm.ui.reporting.util;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;

/**
 * Property column that localizes SCORM status codes using {@code completion.status.*} and {@code success.status.*}
 * bundle keys.
 */
public class StatusPropertyColumn<T> extends PropertyColumn<T, String>
{
	private static final long serialVersionUID = 1L;

	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

	private final String prefix;

	public StatusPropertyColumn(IModel<String> displayModel, String sortProperty, String propertyExpression, String prefix)
	{
		super(displayModel, sortProperty, propertyExpression);
		this.prefix = prefix;
	}

	@Override
	public void populateItem(Item<ICellPopulator<T>> item, String componentId, IModel<T> rowModel)
	{
		IModel<?> dataModel = getDataModel(rowModel);
		Object value = dataModel.getObject();
		String normalized = normalize(value);
		String defaultValue = value == null ? "" : value.toString();
		IModel<String> labelModel = new ResourceModel(prefix + normalized, defaultValue);
		item.add(new Label(componentId, labelModel));
	}

	private String normalize(Object value)
	{
		if (value == null)
		{
			return "";
		}

		String normalized = NON_ALNUM.matcher(value.toString().toLowerCase(Locale.ROOT).trim()).replaceAll("-");
		return StringUtils.strip(normalized, "-");
	}
}
