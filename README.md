
THIS PACKAGE OF SCRIPTS HAVE BEEN DEPRECATED AS OF DECEMBER 2ND, 2015, AND THEIR REPLACEMENT SCRIPTS ARE FOUND HERE: https://github.com/PLOS/production_apex_scripts/tree/master/prepare_zip

This project contains a series of Groovy scrips for preparing archives to ingest into the ambra platform

Prerequisites:

Install groovy
Install imagemagick

Once complete, to run a script, you'll execute a command like the following:

groovy -cp src/main/groovy src/main/groovy/RunGroovy.groovy src/main/groovy/org/topazproject/ambra/sip/PrepareSIP.groovy -c /etc/ambra/ambra.xml -o ~/destination_archive/pone.0068429.zip ~/source_archive/pone.0068429.zip

You may have problems with the class path.  If so, put all jars into a folder called ".groovy/lib/" in your home folder


