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

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.configuration.Configuration
import groovy.util.slurpersupport.GPathResult

/**
 * Create scaled down versions of all images and add them as additional representations
 * to the SIP.
 *
 * @author bill oconnor
 * @author stevec
 * @author Ronald Tschalär
 */
public class ProcessImages {
  /** Map of article image contexts and their associated representations. */
  private static final Map<String, String[]> repsByCtxt = new HashMap<String, String[]>()
  private static final String[] COPYRIGHTS = [
      "Creative Commons Attribution License",
      "Public Domain",
      "Public Library of Science Open-Access License"
  ];

  static {
    String[] smallMediumLarge = [ "PNG_I", "PNG_S", "PNG_M", "PNG_L" ]
    String[] singleLarge      = [ "PNG" ]

    repsByCtxt.put('fig',                 smallMediumLarge)
    repsByCtxt.put('table-wrap',          smallMediumLarge)
    repsByCtxt.put('alternatives',        smallMediumLarge)
    repsByCtxt.put('disp-formula',        singleLarge)
    repsByCtxt.put('chem-struct-wrapper', singleLarge)
    repsByCtxt.put('inline-formula',      singleLarge)
    repsByCtxt.put('striking-image',      smallMediumLarge)
  }

  Configuration config
  ImageUtil     imgUtil
  boolean       verbose

  /** 
   * Create a new image processor. 
   * 
   * @param config  the configuration to use
   * @param verbose if true, print out some info while processing
   */
  public ProcessImages(Configuration config, boolean verbose) {
    this.config  = config
    this.imgUtil = new ImageUtil(config, verbose)
    this.verbose = verbose
  }

  /**
   * Create the scaled images and add them to the sip.
   *
   * @param articleFile the sip
   * @param newName     the new sip file's name, or null to overwrite
   */
  public void processImages(String articleFile, String newName) {
    if (verbose) {
      println('Processing file: ' + articleFile)
    }

    SipUtil.updateZip(articleFile, newName) { articleZip, newZip ->
      // get manifest
      ArchiveEntry me = articleZip.getEntry(SipUtil.MANIFEST)
      if (me == null)
        throw new IOException(
            "No manifest found - expecting one entry called '${SipUtil.MANIFEST}' in zip file")

      def manif = SipUtil.getManifestParser().parse(articleZip.getInputStream(me))

      // get the proper image-set
      def art = SipUtil.getArticle(articleZip, manif)

      //any image files that are created by the image processing (e.g. png_m, etc.)
      Map<String, List<String>> newImageFiles = [:]
      for (entry in articleZip.entries()) {
        if (entry.name == SipUtil.MANIFEST)
          continue              // a new one is written below
        if (isImage(entry.name)) {
          File f = File.createTempFile('tmp_', entry.name)
          f.withOutputStream{ it << articleZip.getInputStream(entry) }
          if (verbose) {
            println 'Created temp image file: ' + f.getCanonicalPath()
          }
          //process the image (add metadata, etc.)
          newImageFiles[entry.name] = processImage(newZip, entry.name, f, art, manif)
        } else {
          //just copy the file straight over
          newZip.copyFrom(articleZip, [entry.name])
        }
      }

      // write out the new manifest
      newZip.putNextEntry(SipUtil.MANIFEST)

      newZip << '<?xml version="1.0" encoding="UTF-8"?>\n'
      newZip << '<!DOCTYPE manifest SYSTEM "manifest.dtd">\n'

      def newManif = new groovy.xml.MarkupBuilder(new OutputStreamWriter(newZip, 'UTF-8'))
      newManif.doubleQuotes = true
      newManif.omitEmptyAttributes = true
      newManif.omitNullAttributes = true

      newManif.'manifest' {
        manif.articleBundle.each{ ab ->
          articleBundle {
            article(uri:ab.article.@uri, 'main-entry':ab.article.'@main-entry') {
              for (r in ab.article.representation) {
                representation(name:r.@name, entry:r.@entry)
                for (img in newImageFiles[r.@entry.text()])
                  representation(name:SipUtil.getRepName(img), entry:img)
              }
            }

            for (obj in ab.object) {
              object(uri:obj.@uri, strkImage:obj.@strkImage) {
                for (r in obj.representation) {
                  representation(name:r.@name, entry:r.@entry)
                  for (img in newImageFiles[r.@entry.text()])
                    representation(name:SipUtil.getRepName(img), entry:img)
                }
              }
            }
          }
        }
      }

      newZip.closeEntry()
    }
  }

  /**
   * check if a zip entry is an image
   * @param entryName the name of the zip entry
   * @return true if the entry is an image, false if not
   */
  private boolean isImage(String entryName){
    entryName = entryName.toLowerCase()
    return entryName.endsWith(".tif") || entryName.endsWith(".png") ||
        entryName.endsWith(".gif") || entryName.endsWith(".tiff") ||
        entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") ||
        entryName.endsWith(".bmp")
  }

  /**
   * Process an image
   * @param newZip
   * @param name
   * @param file
   * @param articleXml
   * @param manifest
   */
  private List<File> processImage(ArchiveOutputStream newZip, String name, File file,
                            GPathResult articleXml, GPathResult manifest) {
    List<File> newImages = []
    String doi = getUri(manifest, name)

    def Map contextRslt = getContextElement(name, doi, articleXml, manifest)
    def context = contextRslt.context
    def contextName = contextRslt.name

    if(!contextName.equals("disp-formula") && !contextName.equals("inline-formula")
            && !contextName.equals("striking-image") ) {
      //add metadata
      String copyright = getCopyright(articleXml)
      String pubDate = getPubDate(articleXml)
      String articleDoi = articleXml.front.'article-meta'.'article-id'.find {it.'@pub-id-type' == 'doi'}.text()
      String title = context.label.text()
      String description = context.caption.text()
      String publisher = articleXml.front.'journal-meta'.publisher.'publisher-name'.text()

      imgUtil.addMetadata(file, pubDate, description, doi, publisher, title, articleDoi, copyright)

      if(verbose) {
        println "added metadata to ${name}"
      }
    }
    if (name.toLowerCase().endsWith('.tif')) {
      String[] reps = repsByCtxt[contextName]
      Configuration imgSet = getImageSet(articleXml)
      //make resized images
      List<File> newFiles = makeResizedImages(name, file, imgSet, reps)
      for(f in newFiles){
        if(verbose){
          println "adding ${f.name} to new zip"
        }
        newZip.writeEntry(f.name, f.length(), f.newInputStream())
        newImages << f.name
        //TODO: generate a png_a from the png_m with the doi appended
        f.delete()
      }
      //TODO: generate a tif_a from the tif with the doi appended
    }
    newZip.writeEntry(name, file.length(), file.newInputStream())
    file.delete()
    return newImages
  }

  private String getPubDate(GPathResult articleXml) {
    def pubNode = articleXml.front.'article-meta'.'pub-date'.find {it.'@pub-type' == 'epub'}
    String year = pubNode.year.text().trim()
    String month = pubNode.month.text().trim()
    month = Integer.valueOf(month) < 10 ? '0' + month : month
    String day = pubNode.day.text().trim()
    day = Integer.valueOf(day) < 10 ? '0' + day : day

    return year + '-' + month + '-' + day
  }

  private List<File> makeResizedImages(String name, File file, Configuration imgSet,
                            String[] reps)
      throws ImageProcessingException {
    if (!reps) {
      return []
    }

    def baseName = name.substring(0, name.lastIndexOf('.') + 1)
    List<File> imgNames = []
    use (CommonsConfigCategory) {
      if (reps.any{ it == 'PNG_I' })
        imgNames.add(imgUtil.resizeImage(file, baseName + 'PNG_I', 'png',
                                         imgSet.inline.'@width'[0]?.toInteger() ?: 70, 0,
                                         imgSet.inline.'@quality'[0]?.toInteger() ?: 70))

      if (reps.any{ it == 'PNG_S' })
        imgNames.add(imgUtil.resizeImage(file, baseName + 'PNG_S', 'png',
            imgSet.small.'@width'[0]?.toInteger() ?: 70, 0,
            imgSet.small.'@quality'[0]?.toInteger() ?: 70))

      if (reps.any{ it == 'PNG_M' })
        imgNames.add(imgUtil.resizeImage(file, baseName + 'PNG_M', 'png',
                                         imgSet.medium.'@maxDimension'[0]?.toInteger() ?: 600,
                                         imgSet.medium.'@maxDimension'[0]?.toInteger() ?: 600,
                                         imgSet.medium.'@quality'[0]?.toInteger() ?: 80))

      String lrg = reps.find{ it == 'PNG_L' || it == 'PNG' }
      if (lrg)
        imgNames.add(imgUtil.resizeImage(file, baseName + lrg, 'png', 0, 0,
                                         imgSet.large.'@quality'[0]?.toInteger() ?: 90))
    }
    return imgNames
  }

  /**
   * Find the configured image-set for the article.
   */
  private Configuration getImageSet(def art) throws IOException {
    def artType = art.front.'article-meta'.'article-categories'.'subj-group'.
                      find{ it.'@subj-group-type' = 'heading' }.subject.text()

    use (CommonsConfigCategory) {
      def name = config.ambra.articleTypeList.articleType.find{ it.typeHeading == artType }?.
                        imageSetConfigName
      name = name ?: '#default'

      def is = config.ambra.services.documentManagement.imageMagick.imageSetConfigs.imageSet.
                      find{ it.'@name' == name }

      if (verbose) {
        println "article-type: ${artType}"
        println "img-set-name: ${name}"
        println "img-set:      ${is ? 'found' : 'not-found, using hardcoded defaults'}"
      }

      return is
    }
  }

  /**
   * Get the type of copyright under which the article is being published
   *
   * @param art the article xml
   * @return the type of copyright
   */
  private String getCopyright(def art){
    def copyright = art.front.'article-meta'.'copyright-statement'.text() //nlm2.0
    if (copyright.isEmpty()) {
      copyright = art.front.'article-meta'.permissions.license.'license-p'.text() //nlm3.0
    }
    for(type in COPYRIGHTS){
      if(copyright.contains(type) || copyright.contains(type.toLowerCase())){
        return type
      }
    }
    println "Unknown copyright type: '${copyright}'"
    return "";
  }

  /**
   * Get the context element in the article for the link that points to the given entry.
   */
  private Map getContextElement(String entryName, String uri, def art, def manif) {
    def linkInArticle = art.'**'*.'@xlink:href'.find { it.text() == uri }

    // If it is a striking-image it will not have a link in the xml
    if (uri.endsWith("strk"))
       return [name:'striking-image', context:null]

    if (!linkInArticle)
       throw new IOException("xlink:href=\"${uri}\" not found in the article")

    def ref = linkInArticle.'..'
    ref = ref.name() == 'supplementary-material' ? ref : ref.'..'

    return [name:ref.name(), context:ref]
  }

  /**
   * Get the uri of a zip entry
   * @param manif
   * @param entryName
   * @return
   */
  private String getUri(manif, String entryName) {
    String uri = manif.articleBundle.object.
        find { it.representation.'@entry'.text().contains(entryName) }.'@uri'.text()
    uri
  }
}
