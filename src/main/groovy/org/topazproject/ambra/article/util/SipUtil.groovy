/* $HeadURL::                                                                                    $
 * $Id$
 *
 * Copyright (c) 2006-2008 by Topaz, Inc.
 * http://topazproject.org
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

package org.topazproject.ambra.article.util

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

import org.topazproject.xml.transform.cache.CachedSource

/**
 * Utilities for dealing with SIP's.
 *
 * @author Ronald Tschalär
 */
public class SipUtil {
  public static final String MANIFEST     = "MANIFEST.xml"
  public static final String MANIFEST_DTD = "manifest.dtd"

  /**
   * Update a zip file. This opens the current zip and a new zip and passes them to the closure;
   * afterwards the zips are closed and the new one is renamed to the current. The closure is
   * responsible for fully populating the new zip, which includes copying over all files from the
   * current zip that should be preserved. The new zip has a helper method,
   * <code>copyFrom(ZipFile, entries)</code>, which can be used to easily copy entries from the
   * current to the new zip.
   *
   * <p>The following example demonstrates a no-op:
   * <pre>
   *   SipUtil.updateZip('pone.0000101.zip', null) { zf, zout -&gt;
   *     zout.copyFrom(zf, zf.entries())
   *   }
   * </pre>
   *
   * @param fname   the filename of the zip
   * @param newName if not-null, the name of the resulting zip; if null, <var>fname</var>
   *                will be overwritten
   * @param closure the closure to run; it will be passed two arguments, the original zip as
   *                a ZipFile, and the new zip as a ZipOutputStream
   * @return whatever was returned by the closure
   */
  public static def updateZip(String fname, String newName, Closure c) throws IOException {
    // open up the zip
    File zip = new File(fname)
    ZipFile zf = new ZipFile(zip)

    // start the new zip file
    if (newName && zip.canonicalFile == new File(newName).canonicalFile)
      newName = null

    File znew = newName ? new File(newName) :
                          File.createTempFile(zip.name, '.tmp', zip.absoluteFile.parentFile)
    ZipOutputStream zout = new ZipOutputStream(znew.newOutputStream())

    // copy/add/whatever
    def res
    boolean ok = false;
    try {
      use (ZipHelperCategory) {
        res = c(zf, zout)
      }
      ok = true
    } finally {
      // cleanup
      try {
        zout.close()
      } catch (IOException ioe) {
      }

      try {
        zf.close()
      } catch (IOException ioe) {
      }

      if (!ok && newName)
        znew.delete()
    }

    if (!newName) {
      if (!zip.delete())
        throw new IOException(
              "Error deleting '${zip}' - please manually rename '${znew}' to '${zip}'")
      if (!znew.renameTo(zip))
        throw new IOException("Error renaming '${znew}' to '${zip}' - please do this manually")
    }

    return res
  }

  /**
   * Get a parser for the manifest.
   *
   * @return the parser
   */
  public static XmlSlurper getManifestParser() {
    XmlSlurper slurper = new XmlSlurper(true, false)
    slurper.setEntityResolver(new ManifestDTDResolver())
    return slurper
  }

  /** 
   * Get the parsed article. 
   * 
   * @param sip      the sip
   * @param manifest the manifest
   * @return the parsed article
   */
  public static def getArticle(ZipFile sip, def manifest) {
    XmlSlurper slurper = new XmlSlurper()
    slurper.setEntityResolver(CachedSource.getResolver())
    ZipEntry ae = sip.getEntry(manifest.articleBundle.article.'@main-entry'.text())
    return slurper.parse(sip.getInputStream(ae))
  }

  /** 
   * Get the representation name for the given entry name. Currently this the extension in
   * upper-case.
   * 
   * @param name the name of the entry
   * @return the representation name
   */
  public static String getRepName(String name) {
    return name.substring(name.lastIndexOf('.') + 1).toUpperCase()
  }

}

private class ZipHelperCategory {
  static ZipOutputStream copyFrom(ZipOutputStream self, ZipFile zf, def entries) {
    for (e in entries) {
      self.putNextEntry(new ZipEntry(e instanceof ZipEntry ? e.name : e.toString()))
      self << zf.getInputStream(e)
      self.closeEntry()
    }

    return self
  }
}

class ManifestDTDResolver implements EntityResolver {
  public InputSource resolveEntity(String publicId, String systemId) {
    if (systemId.endsWith("manifest.dtd"))
      return new InputSource(getClass().getResourceAsStream("manifest.dtd"))
    return null
  }
}

