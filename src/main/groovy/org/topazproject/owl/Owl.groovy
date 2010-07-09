/* $HeadURL:: http://gandalf.topazproject.org/svn/head/plos/libs/runscripts/src/main/gro#$
 * $Id: Owl.groovy 4123 2007-12-03 03:40:28Z pradeep $
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
package org.topazproject.owl

import java.util.Collections
import java.util.HashMap

import org.topazproject.otm.ClassMetadata
import org.topazproject.otm.EntityMode
import org.topazproject.otm.SessionFactory

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
  static SessionFactory factory = new SessionFactoryImpl()

  /**
   * Given a list of directories and/or jar files, generate metadata.
   */
  public static void main(String[] args) {
    factory.preloadFromClasspath()
    generateOwl()
  }

  /**
   * This is a temporary solution to the problem associated with the gmaven
   * plugin. This function accepts the classloader from the calle to ensure
   * classes are loaded correctly. This allows use of mvn -Pgenerate-owl within
   * a development tree. Please note: you will need to temporarily patch
   * AnnotationClassMetaFactory.java to not use the default class loader
   * instead of the context class loader.
   */
   public static generate(ClassLoader cl, String[] args) {
     factory.preloadFromClasspath(cl)
     generateOwl(cl.getResourceAsStream("org/topazproject/owl/ambra.owl"))
   }

  /**
   * Generate the OWL statements
   */
  static void generateOwl(InputStream is) {
    // Add Object to class meta-data
    Map<EntityMode, EntityBinder> binders = new HashMap<EntityMode, EntityBinder>()
    binders.put(EntityMode.POJO, new ClassBinder(Object.class))
    factory.setClassMetadata(new ClassMetadata(binders, "Object", Collections.EMPTY_SET,
                                               Collections.EMPTY_SET, "", null, Collections.EMPTY_SET,
                                               null, Collections.EMPTY_SET, Collections.EMPTY_SET))
    factory.validate()

    OwlGenerator owlGen= new OwlGenerator("http://www.topazproject.org/ambra#", is, factory)
    owlGen.addNamespaces(null, factory.listAliases())
    owlGen.generateClasses()
    owlGen.generateProperties()
    owlGen.save("file:" + System.properties['user.home'] + File.separator + "ambra.owl")
  }
}
