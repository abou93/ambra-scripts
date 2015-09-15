/* $HeadURL::                                                                            $
 * $Id$
 *
 * Copyright (c) 2007-2010 by Public Library of Science
 * http://plos.org
 * http://ambraproject.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.topazproject.ambra.sip

import org.apache.commons.configuration.Configuration
import org.topazproject.ambra.util.ToolHelper
import java.util.regex.Matcher

/**
 * Create scaled down versions of all images and add them as additional representations
 * to the SIP.
 *
 * @author stevec
 * @author Ronald Tschalär
 */
public class ImageUtil {
  boolean verbose
  String  imConvert
  String  imIdentify
  String  imMogrify
  File    tmpDir

  static final String XMP_TEMPLATE = "<?xpacket begin='﻿' id='W5M0MpCehiHzreSzNTczkc9d'?>\n" +
      "<x:xmpmeta xmlns:x='adobe:ns:meta/' x:xmptk='Image::ExifTool 8.60'>\n" +
      "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n" +
      "\n" +
      " <rdf:Description rdf:about=''\n" +
      "  xmlns:dc='http://purl.org/dc/elements/1.1/'>\n" +
      "  <dc:date>\n" +
      "   <rdf:Seq>\n" +
      "    <rdf:li>@@ARTICLE_DATE@@</rdf:li>\n" +
      "   </rdf:Seq>\n" +
      "  </dc:date>\n" +
      "  <dc:description>\n" +
      "   <rdf:Alt>\n" +
      "    <rdf:li xml:lang='x-default'>@@DESCRIPTION@@</rdf:li>\n" +
      "   </rdf:Alt>\n" +
      "  </dc:description>\n" +
      "  <dc:identifier>@@DOI@@</dc:identifier>\n" +
      "  <dc:publisher>\n" +
      "   <rdf:Bag>\n" +
      "    <rdf:li>@@PUBLISHER@@</rdf:li>\n" +
      "   </rdf:Bag>\n" +
      "  </dc:publisher>\n" +
      "  <dc:title>\n" +
      "   <rdf:Alt>\n" +
      "    <rdf:li xml:lang='x-default'>@@TITLE@@</rdf:li>\n" +
      "   </rdf:Alt>\n" +
      "  </dc:title>\n" +
      "  <dc:rights>\n" +
      "   <rdf:Alt>\n" +
      "    <rdf:li xml:lang='x-default'>@@ARTICLE_RIGHTS@@</rdf:li>\n" +
      "   </rdf:Alt>\n" +
      "  </dc:rights>\n" +
      " </rdf:Description>\n" +
      "\n" +
      " <rdf:Description rdf:about=''\n" +
      "  xmlns:photoshop='http://ns.adobe.com/photoshop/1.0/'>\n" +
      "  <photoshop:Source>@@ARTICLE_DOI@@</photoshop:Source>\n" +
      " </rdf:Description>\n" +
      "</rdf:RDF>\n" +
      "</x:xmpmeta>\n" +
      "<?xpacket end='w'?>";

  public ImageUtil(Configuration config, boolean verbose) {
    use (CommonsConfigCategory) {
      def im = config.ambra.services.documentManagement.imageMagick[0]
      imConvert  = im.executablePath ?: 'convert'
      imIdentify = im.identifyPath   ?: 'identify'
      imMogrify =  im.mogrifyPath    ?: 'mogrify'
      tmpDir     = new File(im.tempDirectory ?: System.getProperty('java.io.tmpdir'))
    }
    this.verbose = verbose
  }

  public void addMetadata(File file, String pubDate, String description, String doi,
                          String publisher, String title, String articleDoi, String rights) {
    String xmp = XMP_TEMPLATE
        .replaceFirst("@@ARTICLE_DATE@@", pubDate)
        .replaceFirst("@@DESCRIPTION@@", Matcher.quoteReplacement(description.replaceAll("<", "&lt;").replaceAll(">", "&gt;").trim()))
        .replaceFirst("@@DOI@@", "info:doi/" + doi)
        .replaceFirst("@@PUBLISHER@@", publisher)
        .replaceFirst("@@TITLE@@", title.replaceAll("<", "&lt;").replaceAll(">", "&gt;").trim())
        .replaceFirst("@@ARTICLE_RIGHTS@@", rights)
        .replaceFirst("@@ARTICLE_DOI@@", "info:doi/" + articleDoi)

    File tempXmp = File.createTempFile(file.getName(), ".xmp")
    tempXmp.deleteOnExit()
    tempXmp.withOutputStream{ it << new ByteArrayInputStream(xmp.getBytes()) }
    antExec(imMogrify, "-profile XMP:${tempXmp.canonicalPath} ${file.canonicalPath}")
    tempXmp.delete()
  }

  /** 
   * Resize the given image.
   * 
   * @param inFile  the image to resize
   * @param outName the filename to store it under; if relative then it's stored in the temp
   *                directory
   * @param type    the image type of the output (e.g. 'png')
   * @param width   the new width, or 0 to keep aspect
   * @param height  the new height, or 0 to keep aspect
   * @param quality the image quality to use
   * @return the file containing the resized image
   * @throws ImageProcessingException if an error occurred during resize
   */
  public File resizeImage(File inFile, String outName, String type, int width, int height,
                          int quality)
      throws ImageProcessingException {
    def newFile = new File(tmpDir, outName)
    if (verbose)
      println "Creating ${newFile}"

    if (quality < 0 || quality > 100)
      quality = 100;

    def inName = "${inFile.canonicalPath}"
    if (inName.toLowerCase().endsWith(".tif"))
        inName += "[0]";
    def resize = (width || height) ? "-resize ${width ?: ''}x${height ?: ''}>" : ''
    antExec(imConvert, "\"${inName}\" ${resize} -quality ${quality} " +
                       "\"${type}:${newFile}\"")

    return newFile
  }

  /** 
   * Get the dimensions of the given image.
   * 
   * @param inFile  the image to get the dimensions from
   * @return an array of (width, height)
   * @throws ImageProcessingException if an error occurred trying to get the dimensions
   */
  public int[] getDimensions(File inFile) throws ImageProcessingException {
    def props = antExec(imIdentify, "-quiet -format \"%w %h\" \"${inFile.canonicalPath}\"")
    return props.cmdOut.split().collect{ it.toInteger() } as int[]
  }

  private Properties antExec(exe, args) throws ImageProcessingException {
    def ant = new AntBuilder()
    ant.exec(outputproperty:"cmdOut",
             errorproperty: "cmdErr",
             resultproperty:"cmdExit",
             failonerror: "true",
             executable: exe) {
               arg(line: args)
             }

    if (verbose) {
      println "exe:          ${exe} ${args}"
      println "return code:  ${ant.project.properties.cmdExit}"
      println "stderr:       ${ant.project.properties.cmdErr}"
      println "stdout:       ${ant.project.properties.cmdOut}"
    }

    if (ant.project.properties.cmdExit != '0')
      throw new ImageProcessingException(
                  "Error running '${exe} ${args}', exit-status=${ant.project.properties.cmdExit}")

    return ant.project.properties
  }
}
