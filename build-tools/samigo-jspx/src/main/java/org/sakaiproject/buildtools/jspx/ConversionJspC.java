/*
 * Copyright (c) 2026 The Apereo Foundation
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
package org.sakaiproject.buildtools.jspx;

import org.apache.jasper.JspC;
import org.apache.jasper.compiler.JspxCompiler;

/** Per-operation compiler options and result; no global or thread-local state. */
public class ConversionJspC extends JspC {
    private final boolean fragment;
    private final String defaultResponseCharset;
    private String convertedSource;

    public ConversionJspC(boolean fragment, String defaultResponseCharset) {
        this.fragment = fragment;
        this.defaultResponseCharset = defaultResponseCharset;
    }

    @Override
    public String getCompilerClassName() {
        return JspxCompiler.class.getName();
    }

    public boolean isFragment() {
        return fragment;
    }

    public String getDefaultResponseCharset() {
        return defaultResponseCharset;
    }

    public String getConvertedSource() {
        if (convertedSource == null) {
            throw new IllegalStateException("Jasper did not produce JSPX");
        }
        return convertedSource;
    }

    public void setConvertedSource(String convertedSource) {
        this.convertedSource = convertedSource;
    }
}
