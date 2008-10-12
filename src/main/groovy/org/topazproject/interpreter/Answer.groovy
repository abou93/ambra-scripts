/* $HeadURL::                                                                                     $
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
package org.topazproject.interpreter;

import org.topazproject.otm.query.Results;

/** Base class for itql/rdf types below */
class Value {
  /** The raw value from mulgara */
  String value
  /** The quoted value if quote() was called */
  String quotedValue

  String toString() { return quotedValue ? quotedValue : value }
  int size() { return toString().size() }

  /** 
   * Use the suplied closure(s) to quote our value
   *
   * @param f a closer or list of closures that are passed ourself and must return
   *          the quoted value as a string.
   */
  void quote(f) {
    if (f instanceof Closure) {
      quotedValue = f(this)
    } else {
      quotedValue = null
      f.each() { quotedValue = it(this) }
    }
  }
}

class Literal  extends Value   { Literal(value)  { this.value = value } }
class RdfDate  extends Value   { RdfDate(value)  { this.value = value } } // TODO: extend Literal
class RdfInt   extends Value   { RdfInt(value)   { this.value = value } } // TODO: extend Literal
class Resource extends Value   { Resource(value) { this.value = value } }
class Blank    extends Value   { Blank(value)    { this.value = value } }
class Empty    extends Value   { Empty()         { this.value = ""    } }

/**
 * Represents one row of an itql answer. Some columns may also contain rows and
 * so on if there were subqueries. The flatten() and quote() methods modify 
 * instance data.
 */
class Row {
  def vars
  def hdrs = [ ]
  def vals = [ ]

  Row(res, vars) {
    // TODO: Handle now vars (like count())
    this.vars = vars
    vars.each() { hdrs.add(it) }
    vars.each() { var ->
      def val
      switch (res.getType(var)) {
        case Results.Type.URI:        val = new Resource(res.getString(var)); break
        case Results.Type.BLANK_NODE: val = new Blank(res.getString(var)); break
        case Results.Type.LITERAL:
          val = res.getLiteral(var)
          switch (val.getDatatype()?.toString()) {
            case "http://www.w3.org/2001/XMLSchema#int":  val = new RdfInt(val.value); break
            case "http://www.w3.org/2001/XMLSchema#date": val = new RdfDate(val.value); break
            default: val = new Literal(val.value)
          }
          break
        case Results.Type.SUBQ_RESULTS:
          // TODO: Handle a subquery that returns multiple subrows per row
          Results sqr = res.getSubQueryResults(var)
          if (sqr.next())
            val = new Row(sqr, sqr.variables)
          else
            val = new Empty()
          sqr.close()
          break
        default: val = new Value(res.getString(var));
      }
      vals.add(val)
    }
  }

  /** Flatten any subqueries into a simple row */
  void flatten() {
    // Assume all rows have the same type
    int col = 0
    def newvals = [ ]
    def newhdrs = [ ]
    vals.each() { val ->
      if (val instanceof Row) {
        val.flatten()
        newvals += val.vals
        newhdrs += val.hdrs
      } else {
        newvals.add(val)
        newhdrs.add(hdrs[col])
      }
      col++
    }
    vals = newvals
    hdrs = newhdrs
  }

  // Duck typing: Make Row function as if it was an array of columns
  void quote(f)      { vals.each() { it.quote(f) } }
  def getLengths()   { return vals*.size() }
  String toString()  { return vals.toString() }
  def getAt(int pos) { return vals[pos] }
  def iterator()     { return vals.iterator() }
  def size()         { return vals.size() }
}

/**
 * Represent an answer from itql
 */
class Answer {
  def vars
  def data = [ ]

  /**
   * Construct an Answer
   *
   * @param res should be Result from Session
   */
  Answer(res) {
    vars = res.variables
    println "variables: ${vars}"
    while (res.next())
      data.add(new Row(res, vars))
    res.close();
  }

  /** flatten any subquery results into main query */
  void flatten() {
    data.each() { row ->
      row.flatten()
    }
  }

  def getHeaders() {
    if (data)
      return data[0].hdrs
  }

  /** 
   * quote data with suplied closure(s)
   *
   * @param f a closer or list of closures that are passed ourself and must return
   *          the quoted value as a string.
   */
  def quote(f) {
    data.each() { it.quote(f) }
  }

  /** Return the maximum lengths of all columns */
  def getLengths() {
    def lengths = getHeaders()*.size()
    data.each() { row ->
      def col = 0
      row.getLengths().each() { length ->
        lengths[col] = [ lengths[col], length ].max()
        col++
      }
    }
    return lengths
  }

  // Duck typing helpers ... make us look like our data
  def getAt(int pos) { return data[pos] }
  def iterator()     { return data.iterator() }

  // Closure helpers for quote()

  static def csvQuoteClosure = { v ->
    println "Quoting: ${v.getClass().getName()}: $v"
    switch (v) {
      case Literal: return '"' + v.toString().replace('"', '""') + '"'
      case Resource: return "<$v>"
      default: return v.toString()
    }
  }

  static def rdfQuoteClosure = { v ->
    switch (v) {
      case RdfDate: return v.toString(); break
      case RdfInt:  return v.toString(); break
      case Literal: return "'" + v.toString().replace("'", "\\'") + "'"; break
      case Resource: return "<$v>"; break
      default: return v.toString()
    }
  }

  static def createReduceClosure(aliases) {
    return { v ->
      if (!(v instanceof Resource)) return v.toString()
      for (alias in aliases) {
        if (v.value == alias.value) return v.toString()
        def val = v.toString().replace(alias.value, alias.key + ":")
        if (val != v.toString()) return val
      }
      return v.toString()
    }
  }

  static def createTruncateClosure(int length) {
    return { v ->
      if (!(v instanceof Literal)) return v.toString()
      if (v.toString().size() > length) {
        return v.toString()[0..(length-3)] + "..."
      } else {
        return v.toString()
      }
    }
  }
}
