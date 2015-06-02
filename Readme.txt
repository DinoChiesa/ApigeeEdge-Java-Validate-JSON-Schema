Thursday, 14 May 2015, 20:39

This directory contains the Java source code and Java jars required to
compile a Java callout for Apigee Edge that does JSON Schema
validation.  It uses draft #4 of the JSON Schema standard. 
It relies on json-schema-validator v2.2.6 from com.github.fge. 


Usage:
--------

1. unpack (if you can read this, you've already done that).

2. ./build.sh

3. copy out/JsonSchemaValidatorCallout.jar to your apiproxy/resources/java directory.
   also copy all the lib/*.jar files to the same directory.

4. include a Java callout policy in your
   apiproxy/resources/policies directory. It should look something 
   like this:

    <JavaCallout name='Java-ValidateSchema'>
      <DisplayName>Java-ValidateSchema</DisplayName>
      <Properties>
        <!-- used as the prefix for vars set by this callout -->
        <Property name='varprefix'>jsv</Property>

        <!-- find this schema in the JAR under /resources -->
        <Property name='schema'>schema1.json</Property>
      </Properties>

      <ClassName>com.dinochiesa.jsonschema.ValidatorCallout</ClassName>
      <ResourceURL>java://JsonSchemaValidatorCallout.jar</ResourceURL>
    </JavaCallout>


    The named schema must exist in the JsonSchemaValidatorCallout.jar.
    it should be in the resources directory.  The content of the jar
    should look like this: 

        meta-inf/                                       
        meta-inf/manifest.mf                            
        com/                                            
        com/dinochiesa/                                 
        com/dinochiesa/jsonschema/                      
        com/dinochiesa/jsonschema/ValidatorCallout.class
        resources/                                      
        resources/schema1.json                          

   You can have as many schema in the resources directory as you like. 


5. use pushapi (See https://github.com/carloseberhardt/apiploy)
   or a similar tool to deploy your proxy. 



Dependencies
------------------

Apigee Edge expressions v1.0
Apigee Edge message-flow v1.0
json-schema-validator v2.2.6 from com.github.fge
json-schema-core v1.2.5 from com.github.fge
jackson-databind v2.2.3 from com.fasterxml.jackson
jackson-annotations v2.2.3 from com.fasterxml.jackson
jackson-core v2.2.3 from com.fasterxml.jackson


All these jars must be available on the classpath for the compile to
succeed. The build.sh script should download all of these files for
you, automatically. You could also create a Gradle or maven pom file as
well. 

If you want to download them manually: 

    The first 2 jars are available in Apigee Edge. The first two are
    produced by Apigee; contact Apigee support to obtain these jars to allow
    the compile, or get them here: 
    https://github.com/apigee/api-platform-samples/tree/master/doc-samples/java-cookbook/lib

    The rest are 3rd party jars available on maven.org. 



Notes:
--------

There is one callout class, com.dinochiesa.jsonschema.ValidatorCallout ,
which validates a JSON payload in the request. 

The class always reads the request.content.  You will need to modify the
class if you wish to validate the response.content, or a json that is
stored elsewhere. 

The Callout sets these context variables: 

    <varprefix>_schemaName - the name of the schema obtained from
            Properties

    <varprefix>_isSuccess - true/false, telling whether the
            payload validated against the schema. 

    <varprefix>_error - set only when there is an error


Bugs:
--------

There is no pom.xml file 
