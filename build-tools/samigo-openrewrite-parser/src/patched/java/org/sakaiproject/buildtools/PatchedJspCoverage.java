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
package org.sakaiproject.buildtools;

import org.openrewrite.xml.JspParser;
import org.openrewrite.xml.tree.Xml;

/** Entry point compiled only when measuring the modified OpenRewrite sources. */
public final class PatchedJspCoverage {
    public static void main(String[] args) throws Exception {
        JspParserCoverage.run(args, () -> JspParser.builder().build(),
                source -> source instanceof Xml.JspDocument jsp ? jsp.getEof() : "");
    }
}
