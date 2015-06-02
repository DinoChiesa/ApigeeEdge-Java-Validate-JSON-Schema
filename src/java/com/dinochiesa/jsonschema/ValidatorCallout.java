package com.dinochiesa.jsonschema;

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

@IOIntensive

public class ValidatorCallout implements Execution {

    private ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
    private Map properties; // read-only

    public ValidatorCallout (Map properties) {
        this.properties = properties;
    }

    private String getSchemaName(MessageContext msgCtxt) throws Exception {
        String schemaname = (String) this.properties.get("schema");
        // schemaname is the name of the json resource that holds the schema.
        if (schemaname == null || schemaname.equals("")) {
            throw new IllegalStateException("schema is not specified or is empty.");
        }
        return schemaname;
    }

    private String getVarprefix() throws Exception {
        String varprefix = (String) this.properties.get("varprefix");
        if (varprefix == null || varprefix.equals("")) {
            throw new IllegalStateException("varprefix is not specified or is empty.");
        }
        return varprefix;
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

    public ExecutionResult execute(MessageContext msgCtxt,
                                   ExecutionContext exeCtxt) {
        String varName;
        String varprefix = "unknown";
        try {
            varprefix = getVarprefix();

            Message msg = msgCtxt.getMessage();
            InputStream src = msg.getContentAsStream();
            JsonNode contentJson = mapper.readValue(src, JsonNode.class);

            String schemaName = getSchemaName(msgCtxt);
            // diagnostic purposes
            varName = varprefix + "_schemaName";
            msgCtxt.setVariable(varName, schemaName);

            src = getResourceAsStream(schemaName);
            JsonNode schemaJson = mapper.readValue(src, JsonNode.class);
            final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            final JsonSchema schema = factory.getJsonSchema(schemaJson);

            ProcessingReport report = schema.validate(contentJson);
            varName = varprefix + "_isSuccess";
            msgCtxt.setVariable(varName, report.isSuccess());
        }
        catch (Exception e) {
            e.printStackTrace();
            varName = varprefix + "_error";
            msgCtxt.setVariable(varName, "Exception " + e.toString());
            return ExecutionResult.ABORT;
        }
        return ExecutionResult.SUCCESS;
    }
}
