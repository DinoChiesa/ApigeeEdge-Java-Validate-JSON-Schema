package com.google.apigee.edgecallouts.jsonschema;

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;
import java.util.List;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.IOIntensive;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.message.Message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.StringUtils;

@IOIntensive

public class ValidatorCallout implements Execution {

    private static String _varPrefix = "jsv_";
    private static final String varName(String s) { return _varPrefix + s; }

    private static ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally

    // not sure if threadsafe and globally reusable
    private final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();

    private Map properties; // read-only

    public ValidatorCallout (Map properties) {
        this.properties = properties;
    }

    private boolean getSuppressFault(MessageContext msgCtxt) {
        String suppressFault = (String) this.properties.get("suppress-fault");
        if (StringUtils.isBlank(suppressFault)) { return false; }
        suppressFault = resolvePropertyValue(suppressFault, msgCtxt);
        if (StringUtils.isBlank(suppressFault)) { return false; }
        return suppressFault.toLowerCase().equals("true");
    }

    private JsonNode getSchema(MessageContext msgCtxt) throws Exception {
        String schema = (String) this.properties.get("schema");
        if (schema == null) {
            throw new IllegalStateException("schema is not specified");
        }
        schema = schema.trim();
        if (schema.equals("")) {
            throw new IllegalStateException("schema is empty");
        }

        schema = resolvePropertyValue(schema, msgCtxt);
        if (schema == null || schema.equals("")) {
            throw new IllegalStateException("schema resolves to an empty string");
        }
        schema = schema.trim();
        if (schema.endsWith(".json")) {
            msgCtxt.setVariable(varName("schemaName"), schema);
            return mapper.readValue(getResourceAsStream(schema), JsonNode.class);
        }

        // else, the schema is specified directly in the config file,
        // as a string.
        return mapper.readValue(schema, JsonNode.class);
    }

    // private String getJson(MessageContext msgCtxt) throws Exception {
    //     String jsonref = (String) this.properties.get("jsonref");
    //     String json = null;
    //     // jsonref is the name of the variable that holds the json
    //     if (jsonref == null || jsonref.equals("")) {
    //         throw new IllegalStateException("jsonref is not specified or is empty.");
    //     }
    //     else {
    //         json = msgCtxt.getVariable(jsonref);
    //     }
    //     return json;
    // }

    // If the value of a property value begins and ends with curlies,
    // eg, {apiproxy.name}, then "resolve" the value by de-referencing
    // the context variable whose name appears between the curlies.
    private String resolvePropertyValue(String spec, MessageContext msgCtxt) {
        if (spec.startsWith("{") && spec.endsWith("}") && (spec.indexOf(" ")==-1)) {
            String varname = spec.substring(1,spec.length() - 1);
            String value = msgCtxt.getVariable(varname);
            return value;
        }
        return spec;
    }

    private static InputStream getResourceAsStream(String resourceName)
      throws IOException {
        // forcibly prepend a slash
        if (!resourceName.startsWith("/")) {
            resourceName = "/" + resourceName;
        }
        if (!resourceName.startsWith("/resources")) {
            resourceName = "/resources" + resourceName;
        }
        InputStream in = ValidatorCallout.class.getResourceAsStream(resourceName);

        if (in == null) {
            throw new IOException("resource \"" + resourceName + "\" not found");
        }

        return in;
    }

    private boolean getDebug() {
        String value = (String) this.properties.get("debug");
        if (value == null) return false;
        if (value.trim().toLowerCase().equals("true")) return true;
        return false;
    }

    public ExecutionResult execute(MessageContext msgCtxt, ExecutionContext exeCtxt) {
        try {
            msgCtxt.removeVariable(varName("error"));
            msgCtxt.removeVariable(varName("valid"));
            InputStream src = msgCtxt.getMessage().getContentAsStream();
            JsonNode contentJson = mapper.readValue(src, JsonNode.class);

            // this schema should probably be cached
            JsonNode schemaJson = getSchema(msgCtxt);
            final JsonSchema schema = factory.getJsonSchema(schemaJson);

            ProcessingReport report = schema.validate(contentJson);
            if (getDebug()) {
                msgCtxt.setVariable(varName("report"), report.toString());
            }
            msgCtxt.setVariable(varName("valid"), report.isSuccess());
            if (!report.isSuccess()) {
                msgCtxt.setVariable(varName("error"), "invalid message");
                if (!getSuppressFault(msgCtxt)) { return ExecutionResult.ABORT; }
            }
        }
        catch (Exception e) {
            if (getDebug()) {
                System.out.println(ExceptionUtils.getStackTrace(e));
            }
            String error = e.toString();
            msgCtxt.setVariable(varName("exception"), error);
            int ch = error.lastIndexOf(':');
            if (ch >= 0) {
                msgCtxt.setVariable(varName("error"), error.substring(ch+2).trim());
            }
            else {
                msgCtxt.setVariable(varName("error"), error);
            }
            msgCtxt.setVariable(varName("stacktrace"), ExceptionUtils.getStackTrace(e));
            msgCtxt.setVariable(varName("success"), false);

            if (getSuppressFault(msgCtxt)) return ExecutionResult.SUCCESS;
            return ExecutionResult.ABORT;
        }
        return ExecutionResult.SUCCESS;
    }
}
