#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
#
# Created: <Mon Apr  8 20:19:08 2013>
# Last Updated: <2015-May-14 20:56:14>
#

zipname=java-json-schema-validator

if [ -f $zipname.zip ] ; then
  rm $zipname.zip
fi

zip -r --exclude=*/*.*~  $zipname  src build.sh clean.sh Readme.txt

echo
unzip -l $zipname
echo
