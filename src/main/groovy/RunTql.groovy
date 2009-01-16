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
import org.topazproject.interpreter.Answer;
import org.topazproject.ambra.util.ToolHelper;
import org.topazproject.mulgara.itql.DefaultItqlClientFactory;
import org.topazproject.otm.impl.SessionFactoryImpl;
import org.topazproject.otm.stores.ItqlStore;
import org.topazproject.otm.query.Results;

import jline.ConsoleReader;
import jline.History;

// Constants
MULGARA_BASE = "localhost:8111"
MULGARA_LOC = "/topazproject"

// In case somebody runs %mode = csv instead of %mode = "csv"
csv = "csv"

// Allows %mode = table
table = "table reduce quote"

// Parse command line
def cli = new CliBuilder(usage: 'run-tql [-M mulgarahost:port|-D directory] [-S servername] [-f script] [-T timeout] [-iwtpvN]')
cli.h(longOpt:'help', 'usage information')
cli.v(longOpt:'verbose', 'turn on verbose mode')
cli.e(longOpt:'echo', 'echo script file when running')
cli.w(longOpt:'write-lock', 'transactions should grab a write-lock')
cli.p(longOpt:'prompt', 'show the prompt even for a script file')
cli.N(longOpt:'noprettyprint', 'Do not pretty-print results')
cli.i(longOpt:'runinit', 'Run ~/.runtql even if running a script')
cli.M(args:1, 'Mulgara host:port')
cli.D(args:1, 'directory (embedded mulgara)')
cli.S(args:1, 'the servername, i.e. the path part of the URI used to access mulgara')
cli.T(args:1, 'Transaction timeout in seconds [default: 60]')
cli.f(args:1, 'script file')
cli.m(args:1, 'mode')
cli.t(args:1, 'number of characters to truncate literals to')

def opt = cli.parse(ToolHelper.fixArgs(args))
if (!opt) { cli.usage(); return }
if (opt.h) { cli.usage(); return }
if (opt.M && opt.D) { cli.usage(); return }

def file = (opt.f) ? new File(opt.f).newInputStream() : System.in
bPrompt = (opt.p || !opt.f)
bInit   = (opt.i || !opt.f)
pp = !opt.N
mode = ((opt.m) ?: table)
echo = opt.v || opt.e || !opt.f
trunc = opt.t
writeLock = opt.w
timeout = Integer.decode((opt.T) ?: "0")
running = true
def writer = echo ? new OutputStreamWriter(System.out) : new StringWriter()

def mulgaraPath = opt.S ?: MULGARA_LOC
if (!mulgaraPath.startsWith('/'))
  mulgaraPath = '/' + mulgaraPath

def mulgaraUri, clFactory
if (opt.D) {
  mulgaraUri = "local://${mulgaraPath}"
  clFactory  = new DefaultItqlClientFactory(dbDir: opt.D)
} else {
  def mulgaraBase = opt.M ?: MULGARA_BASE
  mulgaraUri = "rmi://${mulgaraBase}${mulgaraPath}"
  clFactory  = new DefaultItqlClientFactory()
}
verbose = opt.v
if (verbose) {
  println "Mulgara URI: $mulgaraUri"
}

factory = new SessionFactoryImpl(tripleStore:new ItqlStore(mulgaraUri.toURI(), clFactory))
session = factory.openSession()

help = new HashMap()
help[null] = '''All commands are sent to mulgara once a semicolon (;) is found except for
lines starting with #, . or %. Additional help is available via .help <topic>.

These are interpreted as follows:
  # - Comment lines
  % - Remainder of line executed as groovy. See ".help variables"
  . - Runs special commands. See ".help cmds"

Available topics:
  variables cmds .alias .quit init'''
help["cmds"] = '''The following commands are supported: .alias, .quit
Run ".help .<cmd>" for help with a specific command'''
help[".alias"] = """.alias [list|set alias uri]
  list - lists currently active aliases (but doesn't load them)
  set alias uri - adds an alias to the interpreter (but doesn't save it)"""
help[".quit"] = """.quit - Exit the interpreter"""
help["variables"] = '''Variables can be used for a number of things:
  - Controlling features of the interpreter (see list of variables below)
  - As place holders in tql. 
    e.g. tql> %graphX = "<local:///topazproject#foo>"
         tql> select $s $p $o from ${graphX} where $s $p $o;

If a %-character is the first character of the line, the rest of the line is 
sent to the groovy interpreter. Thus, %x=3 sets x to 3. %foo=bar is an error 
because bar is not in quotes. '%println mode' will printout the value of the
variable mode.

Special variables:
  mode (str) - Sets display output. General options are: csv, tsv, table
               You can also append sub-modes: quote reduce
               These quote literals and uris appropriately and/or reduce uris 
               via aliases for easier viewing. eg. %mode="table quote reduce"
  trunc (int)- If set to an integer, literals are truncated to this number of
               characters
  verbose    - If set to true, will output more information'''
help["init"] = "On startup, ~/.runtql is loaded."

// Various functions

def showHelp(args) {
  def desc = help[args == null ? null : args[0]]
  println (desc != null ? desc : "Invalid topic: ${args[0]}")
}

def alias(args) {
  switch(args[0]) {
  case 'list': showAliases(); break
  case 'set' :
    factory.addAlias(args[1], URI.create(args[2]).toString())
    session.close()
    session = factory.openSession()
    break
  case 'help': println ".alias [list|set alias uri]"; break
  }
}

def showAliases() {
  def aliases = factory.listAliases()
  def len = new ArrayList(aliases.keySet())*.size().max()
  aliases.keySet().sort().each() { printf "%${len}s: %s\n", it, aliases[it] }
}

def reduceUri(uri) {
  for (alias in factory.listAliases()) {
    if (uri == alias.value) return uri
    def val = uri.replace(alias.value, alias.key + ":")
    if (val != uri) return val
  }
  return uri
}

def reduce(s) {
  for (alias in factory.listAliases())
    s = s.replaceAll(alias.value, alias.key + ":")
  return s
}

def expand(s) {
  for (alias in factory.listAliases())
    s = s.replaceAll(alias.key + ":", alias.value)
  return s
}

def showResults(result) {
  if (result instanceof String) {
    if (echo) println result
  } else if (result instanceof Results) {
    switch (mode) { case ~/.*csv.*/: showCsv(result); break
      case ~/.*tsv.*/: showTsv(result); break
      case ~/.*tab.*/: showTable(result); break
      default: showTable(result)
    }
  } else {
    println result
  }
}

def showCsv(res) {
  def ans = new Answer(res)
  ans.flatten()
  def ops = [ ]
  if (mode =~ "red") ops.add(ans.createReduceClosure(factory.listAliases()))
  if (mode =~ "quote") ops.add(ans.csvQuoteClosure)
  ans.quote(ops)
  ans.each() { println it.toString()[1..-2] }
}

def showTsv(res) {
  def ans = new Answer(res)
  ans.flatten()
  def ops = [ ]
  if (mode =~ "red") ops.add(ans.createReduceClosure(factory.listAliases()))
  if (mode =~ "quote") ops.add(ans.rdfQuoteClosure)
  ans.quote(ops)
  ans.each() { row ->
    int cols = row.size()
    int col = 0
    row.each() {
      print it
      if (++col < cols) print "\t"
    }
    println()
  }
}

def showTable(res) {
  def ans = new Answer(res)
  def cnt = ans.data.size()
    println "cnt = ${cnt}"
  ans.flatten()
  def ops = [ ]
  if (trunc instanceof Integer && trunc > 3) ops.add(ans.createTruncateClosure(trunc))
  if (mode =~ "red") ops.add(ans.createReduceClosure(factory.listAliases()))
  if (mode =~ "quote") ops.add(ans.rdfQuoteClosure)
  ans.quote(ops)
  def lengths = ans.getLengths()
  def seps = [ ]
  lengths.each() { seps += "-"*it }
  ([ ans.getHeaders(), seps ] + ans.data).each() { row ->
    def col = 0
    def line = ""
    row.each() { val ->
      def st = val.toString()
      line += st + " "*(lengths[col++] - st.size() + 1)
    }
    println line.trim()
  }
  def rowCnt = ans.data.size()
  if (rowCnt == cnt)
    println "${cnt} rows"
  else
    println "${rowCnt} total rows (${cnt} rows from main query)"
}

/**
 * Expand ${} variables in a query string.
 *
 * This allows things like:
 * <pre>
 *   @graph = "<local:///topazproject#mygraph>"
 *   select $s $p $o from ${graph} where $s $p $o;
 * </pre>
 */
def expandVars(query) {
  def result = query
  (query =~ /\$\{([^}]*)}/).each() { st, var ->
    result = result.replace(st, evaluate(var))
  }
  return result
}

def execute(query) {
  try {
    query = expandVars(query)
    if (verbose)
      println query
    doQuery(query)
  } catch (Throwable e) {
    println "Error running query '${query}':"
    Throwable c = e;
    while (c.cause)
      c = c.cause
    println c
    if (verbose)
      e.printStackTrace()
  }
}

void doQuery(query) {
  def tx = session.beginTransaction(!writeLock, timeout)
  try {
    long t0 = System.currentTimeMillis();
    showResults session.doNativeQuery(query.toString())
    if (verbose)
      println "Query took ${System.currentTimeMillis() - t0} ms"
  } catch (Throwable e) {
    // really hacky...
    def m = e.getMessage() =~ /error performing query .* message was: (.*)/
    if (m.count)
      showResults m[0][1]
    else {
      tx.setRollbackOnly()
      throw e
    }
  } finally {
    if (tx.isRollbackOnly()) {
      println "Rolling back transaction..."
      try { tx.rollback() } catch (Throwable t) { if (verbose) t.printStackTrace() }
    } else {
      tx.commit()
    }
  }
}

/** 
 * Ask groovy to evaluate some string in our context
 *
 * Be very careful on refactoring that global variables (really instance variables)
 * are available to evaluate() below or things may get very messy
 */
def eval(s) {
  try {
    this.expand = { expand(it) }
    this.reduce = { reduce(it) }
    this.expandVars = { expandVars(it) }
    evaluate(s)
  } catch (Throwable e) {
    println "Error evaluating groovy: %$s"
    println e
    if (verbose)
      e.printStackTrace()
  }
}

// Handle special commands that start with .
def handleCmd(s) {
  try {
    def args = s.split(/ +/)
    def cmd = args[0]
    args = (args.size() > 1 ? args[1..-1] : null)
    // Look for a matching command (allow abbreviations)
    if      ("alias".startsWith(cmd)) { alias(args) }
    else if ("help".startsWith(cmd))  { showHelp(args) }
    else if ("quit".startsWith(cmd))  { running = false }
    else { println "Unknown command: .$s" }
  } catch (Throwable e) {
    println "Error running command: .$s"
    if (verbose)
      e.printStackTrace()
  }
}

// Queries can exist on multiple lines, so need to stash previous partial queries
query = ""

def processLine(line, console, showPrompt) {
  // strip comments
  if (line != "" && line[0] == '#') 
    line = ''
  // @ is for backward compatibility
  else if (line != "" && (line[0] == '%' || line[0] == '@')) {
    eval(line.substring(1))
    console?.getHistory()?.addToHistory(line)
    line = '' // strip expression
  } else if (line != "" && line[0] == '.') {
    handleCmd(line.substring(1))
    console?.getHistory()?.addToHistory(line)
    // strip expression
    line = ''
  }    

  query += " " + line
  if (query.endsWith(";")) {
    execute query.trim()
    console?.getHistory()?.addToHistory(query)
    query = ""
  }
}

// Read init file if it exists
if (bInit) {
  def initfile = new File(new File(System.getProperty("user.home")), ".runtql")
  if (initfile.exists())
    initfile.eachLine { line -> processLine(line, null, false) }
}

// Show the initial prompt
if (bPrompt)
  println 'Tql Interpreter. Run ".help" for more information.'

// Use jline for some attempt at readline functionality
def cr = new ConsoleReader(file, writer)
if (bPrompt)
  cr.setDefaultPrompt("tql> ")
try {
  histfile = new File(System.getProperty("user.home"), ".runtql_history")
  cr.setHistory(new History(histfile))
  cr.setUseHistory(false)  // we add to history ourself
} catch (IOException e) {
  println "Error loading history: $e"
}

// Loop over lines with jline
while (running && (line = cr.readLine()) != null) {
  processLine(line, cr, true)
}

println()
System.exit(0)
