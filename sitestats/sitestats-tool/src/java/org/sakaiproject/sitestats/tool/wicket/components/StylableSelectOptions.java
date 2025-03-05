/**
 * $URL$
 * $Id$
 *
 * Copyright (c) 2006-2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.sitestats.tool.wicket.components;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.extensions.markup.html.form.select.SelectOption;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;


public class StylableSelectOptions extends RepeatingView {
	private static final long				serialVersionUID	= 1L;
	private boolean							recreateChoices		= false;
	private final IStylableOptionRenderer	renderer;

	public StylableSelectOptions(String id, IModel model, IStylableOptionRenderer renderer) {
		super(id, model);
		this.renderer = renderer;
		setRenderBodyOnly(true);
	}
	
	public StylableSelectOptions(String id, Collection elements, IStylableOptionRenderer renderer) {
		this(id, new Model((Serializable)elements), renderer);
	}

	/**
	 * Controls whether or not SelectChoice objects are recreated every request
	 * 
	 * @param refresh
	 * @return this for chaining
	 */
	public StylableSelectOptions setRecreateChoices(boolean refresh) {
		recreateChoices = refresh;
		return this;
	}

	/**
	 * @see org.apache.wicket.Component#onBeforeRender()
	 */
	@Override
	protected final void onPopulate() {
		if(size() == 0 || recreateChoices){
			// populate this repeating view with SelectOption components
			removeAll();

			Object modelObject = getDefaultModelObject();

			if(modelObject != null){
				if(!(modelObject instanceof Collection)){
					throw new WicketRuntimeException("Model object " + modelObject + " not a collection");
				}

				// iterator over model objects for SelectOption components
				Iterator it = ((Collection) modelObject).iterator();

				while (it.hasNext()){
					// we need a container to represent a row in repeater
					WebMarkupContainer row = new WebMarkupContainer(newChildId());
					row.setRenderBodyOnly(true);
					add(row);

					// we add our actual SelectOption component to the row
					Object value = it.next();
					String text = renderer.getDisplayValue(value);
					IModel model = renderer.getModel(value);
					String style = renderer.getStyle(value);
					String iconClass = renderer.getIconClass(value);
					row.add(newOption("option", model, text, style, iconClass));
				}
			}
		}
	}
	
	protected SelectOption newOption(String id, IModel model, String text, String style, String iconClass) {
		return new StylableSelectOption(id, model, text, style, iconClass);
	}
	
	private static class StylableSelectOption extends SelectOption {
		private static final long	serialVersionUID	= 1L;

		private final String		text;
		private final String		style;
		private final String		iconClass;

		/**
		 * @param id
		 * @param model
		 * @param text
		 */
		public StylableSelectOption(String id, IModel model, String text, String style, String hclass) {
			super(id, model);
			this.text = text;
			this.style = style;
			this.iconClass = hclass;
			setIgnoreAttributeModifier(false);
		}

		@Override
		protected void onComponentTag(ComponentTag tag) {
			super.onComponentTag(tag);
			
			// In Wicket 9, we need to ensure the tag is an option tag
			tag.setName("option");
			
			if(style != null && !"null".equals(style)) {
				tag.put("style", style);
			}
			if(iconClass != null && !"null".equals(iconClass)) {
				tag.put("class", iconClass);
			}
			
			// Set the option text as the tag body
			tag.put("value", getDefaultModelObjectAsString());
		}
		
		@Override
		public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {
			replaceComponentTagBody(markupStream, openTag, text);
		}
	}
}
