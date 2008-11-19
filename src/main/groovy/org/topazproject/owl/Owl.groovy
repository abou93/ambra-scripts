/* $HeadURL:: http://gandalf.topazproject.org/svn/head/plos/libs/runscripts/src/main/gro#$
 * $Id: Owl.groovy 4123 2007-12-03 03:40:28Z pradeep $
 *
 * Copyright (c) 2007-2008 by Topaz, Inc.
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
package org.topazproject.owl

import java.util.Collections
import java.util.HashMap
import java.util.jar.JarFile

import org.topazproject.otm.ClassMetadata
import org.topazproject.otm.EntityMode
import org.topazproject.otm.OtmException
import org.topazproject.otm.SessionFactory

import org.topazproject.otm.criterion.DetachedCriteria

import org.topazproject.otm.impl.SessionFactoryImpl

import org.topazproject.otm.mapping.EntityBinder
import org.topazproject.otm.mapping.java.ClassBinder

import org.topazproject.otm.owl.OwlGenerator

import org.topazproject.ambra.util.ToolHelper

/**
 * Groovy script that figures out OTM annotated classes and passed them to
 * OwlGenerator to generate OWL schema.
 *
 * @author Eric Brown
 * @author Amit Kapoor
 */
class Owl {
  static GroovyClassLoader gcl = null
  static SessionFactory factory = new SessionFactoryImpl()

  /**
   * Given a list of directories and/or jar files, generate metadata.
   */
  public static void generate(ClassLoader parent, String[] args) {
    gcl = new GroovyClassLoader(parent)
    addClasses(ToolHelper.fixArgs(args))
    generateOwl()
  }

  /**
   * Extract otm annotated meatadata from classes found in directories and/or jar files.
   *
   * @param classPaths a collection of jar filenames and/or directories
   */
  static void addClasses(classPaths) {
    // Update gcl classpath first
    classPaths.each() { fname ->
      def file = expandFilename(fname)
      if (file.isDirectory() || fname =~ /\.jar$/) {
        gcl.addClasspath(file.getAbsolutePath())
      } else
        println "$fname not a jar file or directory"
    }

    // Process classes
    classPaths.each() { fname ->
      def file = expandFilename(fname)
      if (fname =~ /\.jar$/)
        addJar(file)
      else if (file.isDirectory())
        addDirectory(file)
    }

    // Add Object to class meta-data
    Map<EntityMode, EntityBinder> binders = new HashMap<EntityMode, EntityBinder>();
    binders.put(EntityMode.POJO, new ClassBinder(Object.class));
    factory.setClassMetadata(new ClassMetadata(binders, "Object", Collections.EMPTY_SET,
                                               Collections.EMPTY_SET, "", null, Collections.EMPTY_SET,
                                               null, Collections.EMPTY_SET, Collections.EMPTY_SET));
    
    // DetachedCriteria
    binders = new HashMap<EntityMode, EntityBinder>();
    binders.put(EntityMode.POJO, new ClassBinder(DetachedCriteria.class));
    factory.setClassMetadata(new ClassMetadata(binders, "DetachedCriteria", Collections.EMPTY_SET,
                                               Collections.EMPTY_SET, "", null, Collections.EMPTY_SET,
                                               null, Collections.EMPTY_SET, Collections.EMPTY_SET));

    factory.validate()
  }

  /**
   * Generate the OWL statements
   */
  static void generateOwl() {
    OwlGenerator owlGen= new OwlGenerator("http://www.plos.org/content_model#", (SessionFactory)factory)
    owlGen.addNamespaces(factory.listAliases())
    owlGen.generateClasses()
    owlGen.generateClassObjectProperties()
    owlGen.generateClassDataProperties()
    owlGen.save("file:" + System.properties['user.home'] + File.separator + "ambra.owl")
  }

  /**
   * Extract class files from jar
   */
  static void addJar(File file) {
    def jarfile = new JarFile(file)
    jarfile.entries().each() {
      def name = it.toString()
      if (name =~ /\.class$/) {
        def clazz = getClass(name)
        if (clazz)
          processClass(clazz)
      }
    }
  }

  /** 
   * Iterate over all the files in a directory and find any otm classes.
   */
  static void addDirectory(File dir) {
    dir.eachFileRecurse() { fname ->
      if (fname =~ /\.class$/) {
        def name = fname.getAbsolutePath() - dir.getAbsolutePath()
        name = name.replaceFirst(/^\//, "")
        def clazz = getClass(name)
        if (clazz)
          processClass(clazz)
      }
    }
  }

  /** 
   * See if a class is otm annotated and add it to our factory.
   */
  static void processClass(Class clazz) {
    try {
      factory.preload(clazz)
    } catch (OtmException o) {
      println "OTM Exception loading " + clazz.getName() + " : $o"
    }
  }

  /**
   * Convert a filename to a classname and load it via our GroovyClassLoader
   */
  static Class getClass(String name) {
    def cName = name.replaceAll(/\//, ".") - ".class"
    try {
      return gcl.loadClass(cName)
    } catch (NoClassDefFoundError ncdfe) {
      println "$ncdfe (loading $cName)"
      return null
    }
  }

  /**
   * Deal with ~ at the beginning of a file or directory
   */
  static File expandFilename(String name) {
    return new File(name.replaceFirst(/^~/, System.getProperty("user.home")))
  }
}
