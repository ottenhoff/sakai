<?xml version="1.0" encoding="UTF-8" ?>
<!--
 * <p>Copyright: Copyright (c) 2005 Sakai</p>
 * <p>Description: QTI Persistence XML to XML Transform for Import</p>
 * @author <a href="mailto:esmiley@stanford.edu">Ed Smiley</a>
 * @version $Id: extractSection.xsl,v 1.3 2005/04/27 02:38:35 esmiley.stanford.edu Exp $
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" doctype-public="-//W3C//DTD HTML 4.01//EN"
 doctype-system="http://www.w3.org/TR/html4/strict.dtd"/>

<xsl:template match="/">
  <sectionData>
   <ident><xsl:value-of select="//section/@ident" /></ident>
   <title><xsl:value-of select="//section/@title" /></title>
    <!-- our metadata -->
    <!-- Prefer matching by fieldlabel to avoid order brittleness -->
    <description>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='SECTION_INFORMATION']/fieldentry"/>
    </description>
    <objective>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='SECTION_OBJECTIVE']/fieldentry"/>
    </objective>
    <keyword>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='SECTION_KEYWORD']/fieldentry"/>
    </keyword>
    <rubric>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='SECTION_RUBRIC']/fieldentry"/>
    </rubric>
    <attachment>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='ATTACHMENT']/fieldentry"/>
    </attachment>
    <questions-ordering>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='QUESTIONS_ORDERING']/fieldentry"/>
    </questions-ordering>

    <!-- Single pool metadata (back-compat) -->
    <pool_id>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='POOLID_FOR_RANDOM_DRAW']/fieldentry"/>
    </pool_id>
    <pool_name>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='POOLNAME_FOR_RANDOM_DRAW']/fieldentry"/>
    </pool_name>
    <num_questions>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='NUM_QUESTIONS_DRAWN']/fieldentry"/>
    </num_questions>
    <randomization_type>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='RANDOMIZATION_TYPE']/fieldentry"/>
    </randomization_type>
    <point_value>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='POINT_VALUE_FOR_QUESTION']/fieldentry"/>
    </point_value>
    <discount_value>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='DISCOUNT_VALUE_FOR_QUESTION']/fieldentry"/>
    </discount_value>

    <!-- Multiple pool support -->
    <random_pool_count>
      <xsl:value-of select="//section/qtimetadata/qtimetadatafield[fieldlabel='RANDOM_POOL_COUNT']/fieldentry"/>
    </random_pool_count>

    <!-- Exported labels like POOLID_FOR_RANDOM_DRAW_1, _2, etc. Map to pool_id_1, pool_id_2 ... -->
    <xsl:for-each select="//section/qtimetadata/qtimetadatafield[starts-with(fieldlabel,'POOLID_FOR_RANDOM_DRAW_')]"><!-- list all suffixed pool ids -->
      <xsl:variable name="suffix" select="substring-after(fieldlabel,'POOLID_FOR_RANDOM_DRAW_')"/>
      <xsl:element name="{concat('pool_id_', $suffix)}">
        <xsl:value-of select="fieldentry"/>
      </xsl:element>
    </xsl:for-each>
    <xsl:for-each select="//section/qtimetadata/qtimetadatafield[starts-with(fieldlabel,'POOLNAME_FOR_RANDOM_DRAW_')]"><!-- list all suffixed pool names -->
      <xsl:variable name="suffix" select="substring-after(fieldlabel,'POOLNAME_FOR_RANDOM_DRAW_')"/>
      <xsl:element name="{concat('pool_name_', $suffix)}">
        <xsl:value-of select="fieldentry"/>
      </xsl:element>
    </xsl:for-each>
  </sectionData>
</xsl:template>

</xsl:stylesheet>
