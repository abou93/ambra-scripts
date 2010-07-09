/* $HeadURL::                                                                                     $
 * $Id$
 *
 * Copyright (c) 2006-2010 by Public Library of Science
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
package org.topazproject.interpreter

import org.topazproject.otm.Session;
import org.topazproject.otm.impl.SessionFactoryImpl;
import org.topazproject.otm.stores.ItqlStore;

class AnswerTest extends GroovyTestCase {
  def aliases = [ "xsd": "http://www.w3.org/2001/XMLShema#",
                  "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  "jrn": "info:doi/10.1371/" ]

  void testParsing() {
    def sess = new SessionFactoryImpl(tripleStore: new ItqlStore("mem:/topazproject".toURI())).
               openSession()
    def tx = sess.beginTransaction()
    try {
      sess.doNativeUpdate("create <mem:/topazproject#test> <http://mulgara.org/mulgara#MemoryModel>;")
      sess.doNativeUpdate("""insert
          <info:doi/10.1371/journal.pone.0000005> <foo:hasTitle> 'Concentration of the Most-Cited Papers'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasTitle> 'PLoS ONE Sandbox: A Place to Learn and Play'
          <info:doi/10.1371/journal.pone.0000005> <foo:hasFoo> '1'
          <info:doi/10.1371/journal.pone.0000005> <foo:hasFoo> '2'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '1'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '2'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '3'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '4'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '5'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '6'
          <info:doi/10.1371/journal.pone.0000000> <foo:hasFoo> '7'
          into <mem:/topazproject#test>;""")

      def q = '''select $article
                   subquery(select $article $title from <mem:/topazproject#test> where $article <foo:hasTitle> $title)
                   count(select $foo from <mem:/topazproject#test> where $article <foo:hasFoo> $foo)
                 from <mem:/topazproject#test> where $article $p $o order by $article;'''
      def ans = new Answer(sess.doNativeQuery(q))

      assert ans != null
      assert ans.getHeaders()[0] == "article"
      assert ans.getHeaders().size() == 3
      ans.flatten()
      assert ans.getHeaders()[2] == "title"
      ans.quote(Answer.createReduceClosure(aliases))
      assert ans[0][1].toString() == "jrn:journal.pone.0000000"
      assert ans.getLengths()[2] == 43
      ans.quote([Answer.createTruncateClosure(30), Answer.rdfQuoteClosure])
      assert ans[1][2].toString() == "'Concentration of the Most-Ci...'"
      //println ans.getHeaders()
      //ans.data.each() { println it }
    } finally {
      tx.rollback()
      sess.close()
    }
  }
}
