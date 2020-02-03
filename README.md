# Java callout - for JSON Schema Validation

This directory contains the Java source code and Java jars required to compile a
Java callout for Apigee Edge that performs JSON Schema validation.  It uses
draft #4 of the [JSON
Schema](https://tools.ietf.org/html/draft-wright-json-schema-00) standard.  For
more information on JSON Schema, see [Understanding JSON
Schema](https://spacetelescope.github.io/understanding-json-schema/structuring.html).
This Java callout relies on [the json-schema-validator library v2.2.10 from
com.github.fge](https://github.com/daveclayton/json-schema-validator).

You must compile the Java code in order to use this callout. See below in the
"Building" section for how to do it.

>  Not really. But it's the easiest way.  Running the build downloads all the
>  dependency jars, and allows you to ensure you have them correct.


## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

## License

This material is Copyright 2015, 2016 Apigee Corporation, Copyright 2018 Google LLC
and is licensed under the [Apache 2.0 License](LICENSE). This includes the Java code as well as the API Proxy configuration.

## Usage

The Java JAR here can be used as a Java callout in Apigee Edge. It can
be configured to run in any flow. A typical use is that you would
configure a Java callout with this JSON Schema Validator class in it, to
run on the request flow, to check the inbound payload from a client against a schema that is attached to the proxy in some way.

The callout always reads the payload from the message.content. When applied on the
request flow, it reads the JSON payload from the request content. When
applied on the response flow, the policy reads the JSON payload from the
response content.

There is one key configuration parameter: the schema. There are several options:

- inline in the policy
- as a variable holding a schema as a string
- as a named schema that is compiled into the JAR
- as a variable referencing a named schema

Examples follow.

### Inline Schema

The first is to specify the schema directly in the policy configuration, like this:

```xml
<JavaCallout name='Java-ValidateSchema-1'>
  <Properties>
    <Property name='schema'>{
  "$schema": "http://json-schema.org/draft-04/schema#",

  "definitions": {
    "address": {
      "type": "object",
      "properties": {
        "street_address": { "type": "string" },
        "city":           { "type": "string" },
        "state":          { "type": "string" }
      },
      "required": ["street_address", "city", "state"]
    }
  },

  "type": "object",

  "properties": {
    "billing_address": { "$ref": "#/definitions/address" },
    "shipping_address": {
      "allOf": [
        { "$ref": "#/definitions/address" },
        { "properties":
          { "type": { "enum": [ "residential", "business" ] } },
          "required": ["type"]
        }
      ]
    }
  }
}
</Property>
  </Properties>

  <ClassName>com.google.apigee.edgecallouts.jsonschema.ValidatorCallout</ClassName>
  <ResourceURL>java://edge-custom-json-schema-validator-20200203.jar</ResourceURL>
</JavaCallout>
```

### Schema in a Variable

Alternatively, you can specify a context variable within curly braces that
has a JSON schema string in it.

```xml
<JavaCallout name='Java-ValidateSchema-2'>
  <Properties>
    <Property name='schema'>{context-var-that-holds-schema}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.jsonschema.ValidatorCallout</ClassName>
  <ResourceURL>java://edge-custom-json-schema-validator-20200203.jar</ResourceURL>
</JavaCallout>
```

For this option, you would need to read the schema from some other source. That might be an external URL, read with a ServiceCallout policy.  Or it could be read from a KVM, using a KVM-Get.  You could even use an AssignMessage. One way or the other, you need to get the schema into a context variable in Apigee Edge.


### Schema in a Resource file

You can also specify a schema file to be found in the /resources
directory of the JAR that contains the callout class. The string must
end with the 5 characters ".json" in order to be recognized as a schema
file. You can specify the schema file name this way:

```xml
<JavaCallout name='Java-ValidateSchema'>
  <DisplayName>Java-ValidateSchema</DisplayName>
  <Properties>
    <!-- find this schema in the JAR under /resources -->
    <Property name='schema'>schema1.json</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.jsonschema.ValidatorCallout</ClassName>
  <ResourceURL>java://edge-custom-json-schema-validator-20200203.jar</ResourceURL>
</JavaCallout>
```

This requires that you bundle the schema file into the JAR; in other words, you must recompile the JAR.


The named schema must exist in the edge-custom-json-schema-validator-20200203.jar.
It should be in the resources directory.  The content of the jar
should look like this:

        meta-inf/
        meta-inf/manifest.mf
        com/
        com/google
        com/google/apigee/edgecallouts
        com/google/apigee/edgecallouts/jsonschema/
        com/google/apigee/edgecallouts/jsonschema/ValidatorCallout.class
        resources/
        resources/schema1.json

You can just drop schema files into the [resources](src/main/resources)
directory and rebuild with maven, to make this happen.


### Schema in a Variable that refers to a Resource file

Finally, you can specify a context variable that contains the schema
file name. This variable will get resolved at runtime. The content of
the variable must end with the 5 characters ".json" in order to be
recognized as a schema file.  The syntax looks like this:

```xml
<JavaCallout name='Java-ValidateSchema'>
  <DisplayName>Java-ValidateSchema</DisplayName>
  <Properties>
    <!-- find this schema in the JAR under /resources -->
    <Property name='schema'>{context_var_that_contains_name_of_schema_resource}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.jsonschema.ValidatorCallout</ClassName>
  <ResourceURL>java://edge-custom-json-schema-validator-20200203.jar</ResourceURL>
</JavaCallout>
```

As above, this also requires that you bundle the referenced schema file into the JAR as a resource.

## Behavior

By default, the Java callout will return ExecutionResult.ABORT, and implicitly put the proxy flow into a Fault state, when:

* the configuration of the policy is incorrect. For example, if there is no schema property present, or if the thing specified for a schema is invalid (not a real schema, or has a typo, etc).
* the schema validation fails.

You can suppress the faults by using a property in the configuration, like this:

```xml
<JavaCallout name='Java-ValidateSchema-2'>
  <Properties>
    <Property name='suppress-fault'>true</Property>
    <Property name='schema'>{context-var-that-holds-schema}</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.jsonschema.ValidatorCallout</ClassName>
  <ResourceURL>java://edge-custom-json-schema-validator-20200203.jar</ResourceURL>
</JavaCallout>
```

Whether or not the policy throws a fault, the policy sets these variables:

| variable name  | meaning                                              |
|:---------------|:---------------------------------------------------- |
| jsv_valid      | true if the JSON message was valid. false if not.    |
| jsv_error      | null if no error. a string indicating the error if the message was invalid, or if there was another error (eg, invalid configuration) |



## Building

Build the project with [maven](https://maven.apache.org/).  Like so:

```
  mvn clean package
```

## Dependencies

At runtime, there are various JAR dependencies.  All of these must be in the resources/java folder of your API Proxy, or must be available as resources in the environment or organization. Check the pom file for the list, or run `mvn dependency:tree`.


## The Example Proxy Bundle

There is an example proxy bundle [included here](./bundle) .
To use it, import and deploy it into an org/env.

Then, demonstrate the success case:


```
ORG=myorg
ENV=myenv
curl -i https://$ORG-$ENV.apigee.net/jsvexample/t1 -H content-type:application/json -d '{
  "shipping_address": {
    "street_address": "1600 Pennsylvania Avenue NW",
    "city": "Washington",
    "state": "DC",
    "type": "business"
  }
}'
```

Now the failure case (missing the address type):
```
curl -i https://$ORG-$ENV.apigee.net/jsvexample/t1 -H content-type:application/json -d '{
  "shipping_address": {
    "street_address": "1600 Pennsylvania Avenue NW",
    "city": "Washington",
    "state": "DC"
  }
}'
```


## Bugs

none?

