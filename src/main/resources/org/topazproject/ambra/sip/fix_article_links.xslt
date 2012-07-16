<?xml version="1.0" encoding="UTF-8"?>
<!--
  $HeadURL::                                                                                      $
  $Id$

 Copyright (c) 2006-2010 by Public Library of Science
 http://plos.org
 http://ambraproject.org

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
 
      http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<!DOCTYPE xsl:stylesheet [
    <!ENTITY nlmpub  "http://dtd.nlm.nih.gov/publishing/">
]>

<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:my="my:ingest.pmc#"
    exclude-result-prefixes="my">

  <!--
    - Fix relative links in article. All links pointing to entry names in the
    - sip are replaced with entry's uri. This also normalizes all doi: links
    - to info:doi/ links.
    -->

  <xsl:param name="manifest" as="document-node(element(manifest))"/>
  <xsl:param name="doiPrefix">info:doi</xsl:param>

  <xsl:preserve-space elements="*"/>

  <!-- 1/4/12: deleted unused DTDs -->
  <xsl:output name="nlm-2.0"
      doctype-public="-//NLM//DTD Journal Publishing DTD v2.0 20040830//EN"
      doctype-system="&nlmpub;2.0/journalpublishing.dtd"/>
  <xsl:output name="nlm-3.0"
      doctype-public="-//NLM//DTD Journal Publishing DTD v3.0 20080202//EN"
      doctype-system="&nlmpub;3.0/journalpublishing3.dtd"/>

  <!-- transform and write out the article -->
  <xsl:template match="/">
    <xsl:result-document method="xml" format="nlm-{article/@dtd-version}">
      <xsl:apply-templates/>
    </xsl:result-document>
  </xsl:template>

  <!-- Article Mods -->
  <xsl:template match="@xlink:href" priority="5">
    <xsl:attribute name="xlink:href"><xsl:value-of select="my:fixup-link(.)"/></xsl:attribute>
  </xsl:template>

  <!-- 12/14/11: modify copy -->
  <xsl:template match="@*|node()">
    <xsl:copy copy-namespaces="no">
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>
  
  <!-- 12/14/11: remove xsi namespace from <article> (added by 3.0 dtd) -->
  <xsl:template match="article">
    <xsl:element name="{name(.)}" namespace="{namespace-uri(.)}">
      <xsl:copy-of copy-namespaces="no" select="namespace::*[name(.)!='xsi']"/>
      <xsl:copy-of copy-namespaces="no" select="@*"/>
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <!-- 12/14/11: remove xml:lang attribute from journal-title (added by 3.0 dtd)-->
  <xsl:template match="journal-title/@xml:lang"/>
  
  <!-- 12/14/11: remove orientation attribute from all elements to which 3.0 dtd adds it -->
  <xsl:template match="@orientation"/>

  <!-- Fix up a link: doi: -> info:doi/, entry-names -> uri's -->
  <xsl:function name="my:fixup-link" as="xs:string">
    <xsl:param name="href" as="xs:string"/>
    <xsl:sequence select="
      if (my:uri-is-absolute($href)) then
        (: doi-uri normalization: 'doi:/DOI' -> 'info:doi/DOI' :)
        if (starts-with($href, 'doi:')) then
          concat($doiPrefix, '/', substring($href, 5))
        else
          $href
      else if ($href = $manifest/manifest/articleBundle/*/representation/@entry) then
        $manifest/manifest/articleBundle/*[representation/@entry = $href]/@uri
      else
        $href (: don't generate an error as the validation will later catch this :)
      "/>
  </xsl:function>

  <!-- Check if the URI is absolute -->
  <xsl:function name="my:uri-is-absolute" as="xs:boolean">
    <xsl:param name="uri" as="xs:string"/>
    <xsl:sequence select="matches($uri, '^[^:/?#]+:')"/>
  </xsl:function>
</xsl:stylesheet>
