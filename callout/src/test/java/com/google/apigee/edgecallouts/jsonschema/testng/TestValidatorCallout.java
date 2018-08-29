package com.google.apigee.edgecallouts.jsonschema.testng;






import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.apigee.edgecallouts.jsonschema.ValidatorCallout;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import mockit.Mock;
import mockit.MockUp;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestValidatorCallout {
    private final static String testDataDir = "src/test/resources/test-data";

    MessageContext msgCtxt;
    String messageContent;
    Message message;
    ExecutionContext exeCtxt;

    @BeforeMethod()
    public void testSetup1() {
        messageContent = null;

        msgCtxt = new MockUp<MessageContext>() {
            private Map variables;
            public void $init() {
                variables = new HashMap();
            }

            @Mock()
            public <T> T getVariable(final String name){
                if (variables == null) {
                    variables = new HashMap();
                }
                return (T) variables.get(name);
            }

            @Mock()
            public boolean setVariable(final String name, final Object value) {
                if (variables == null) {
                    variables = new HashMap();
                }
                variables.put(name, value);
                return true;
            }

            @Mock()
            public boolean removeVariable(final String name) {
                if (variables == null) {
                    variables = new HashMap();
                }
                if (variables.containsKey(name)) {
                    variables.remove(name);
                }
                return true;
            }

            @Mock()
            public Message getMessage() {
                return message;
            }
        }.getMockInstance();

        exeCtxt = new MockUp<ExecutionContext>(){ }.getMockInstance();

        message = new MockUp<Message>(){
            @Mock()
            public InputStream getContentAsStream() {
                if (messageContent == null){ return new ByteArrayInputStream(new byte[0]); }
                return new ByteArrayInputStream(messageContent.getBytes(StandardCharsets.UTF_8));
            }
        }.getMockInstance();
    }

    @DataProvider(name = "batch1")
    public static Object[][] getDataForBatch1()
        throws IOException, IllegalStateException, Exception {

        // @DataProvider requires the output to be a Object[][]. The inner
        // Object[] is the set of params that get passed to the test method.
        // So, if you want to pass just one param to the constructor, then
        // each inner Object[] must have length 1.

        ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Path currentRelativePath = Paths.get("");
        // String s = currentRelativePath.toAbsolutePath().toString();
        // System.out.println("Current relative path is: " + s);

        // read in all the *.json files in the test-data directory
        File testDir = new File(testDataDir);
        if (!testDir.exists()) {
            throw new IllegalStateException("no test directory.");
        }
        File[] files = testDir.listFiles();
        Arrays.sort(files);
        if (files.length == 0) {
            throw new IllegalStateException("no tests found.");
        }
        int c=0;
        ArrayList<TestCase> list = new ArrayList<TestCase>();
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("test") && name.endsWith(".json")) {
                TestCase tc = mapper.readValue(file, TestCase.class);
                tc.setTestName(name.substring(0,name.length()-5));
                if (tc.getExpected()==null) {
                    throw new Exception("invalid test case");
                }
                if (StringUtils.isEmpty(tc.getDescription())) { tc.setDescription("-no description-"); }
                for (String key : tc.getContext().keySet()) {
                    String value = tc.getContext().get(key);
                    if (value.startsWith("file://")) {
                        name = value.substring(7);
                        Path path = Paths.get(testDataDir, name);
                        if (!Files.exists(path)) {
                            throw new IOException("file("+name+") for context("+ key +") not found");
                        }
                        InputStream in = Files.newInputStream(path);
                        // replace the content with what is read from the file
                        tc.getContext().put(key,IOUtils.toString(in,StandardCharsets.UTF_8));
                    }
                }
                list.add(tc);
            }
        }

        // OMG!!  Seriously? Is this the easiest way to generate a 2-d array?
        int n = list.size();
        Object[][] data = new Object[n][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Object[]{ list.get(i) };
        }
        return data;
    }

    @Test
    public void testDataProviders() throws IOException, Exception {
        Assert.assertTrue(getDataForBatch1().length > 0);
    }

    @Test(dataProvider = "batch1")
    public void test2_Configs(TestCase tc) {
        // if (tc.getDescription()!= null)
        //     System.out.printf("  %10s - %s\n", tc.getTestName(), tc.getDescription() );
        // else
        //     System.out.printf("  %10s\n", tc.getTestName() );

        ValidatorCallout callout = new ValidatorCallout(tc.getProperties());  // properties
        for (String key : tc.getContext().keySet()) {
            msgCtxt.setVariable(key,tc.getContext().get(key));
        }

        if (tc.getContext().containsKey("message.content")) {
            // This will be provided as a readable stream, by the msgCtxt.
            messageContent = tc.getContext().get("message.content");
        }

        // execute and retrieve output
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        ExecutionResult expectedResult = ("success".equals((String) tc.getExpected().get("result"))) ?
            ExecutionResult.SUCCESS : ExecutionResult.ABORT;

        // check result and output
        if (expectedResult != actualResult) {
            System.err.printf("  FAIL %10s - %s\n", tc.getTestName(), tc.getDescription());
            System.err.printf("    got: %s\n", actualResult);
            System.err.printf("    expected: %s\n", expectedResult);
            // the following will throw
            Assert.assertEquals(actualResult, expectedResult, "result not as expected");
        }
        else if (expectedResult == ExecutionResult.ABORT) {
            String expectedError = (String) tc.getExpected().get("error");
            Assert.assertTrue(StringUtils.isNotEmpty(expectedError), "expected error");
            String actualError = msgCtxt.getVariable("jsv_error");
            if (expectedError.equals(actualError)) {
                System.out.printf("  PASS %10s - %s\n", tc.getTestName(), tc.getDescription() );
            }
            else {
                System.err.printf("  FAIL %10s - %s\n", tc.getTestName(), tc.getDescription());
                System.err.printf("    got: (%s)\n", actualError);
                System.err.printf("    expected: (%s)\n", expectedError);
                // the following will throw
                Assert.assertEquals(actualError, expectedError, "result not as expected");
            }
        }
        else {
            // completed successfully as expected.  Now let's see if it was valid
            String expectedError = (String) tc.getExpected().get("error");
            if (StringUtils.isNotBlank(expectedError)) {
                String actualError = msgCtxt.getVariable("jsv_error");
                if (expectedError.equals(actualError)) {
                    System.out.printf("  PASS %10s - %s\n", tc.getTestName(), tc.getDescription() );
                }
                else {
                    System.err.printf("  FAIL %10s - %s\n", tc.getTestName(), tc.getDescription());
                    System.err.printf("    got: (%s)\n", actualError);
                    System.err.printf("    expected: (%s)\n", expectedError);
                    // the following will throw
                    Assert.assertEquals(actualError, expectedError, "result not as expected");
                }

            }
            else {
                boolean expectedValid = (boolean) tc.getExpected().get("valid");
                boolean actualValid = msgCtxt.getVariable("jsv_valid");
                if (expectedValid == actualValid) {
                    System.out.printf("  PASS %10s - %s\n", tc.getTestName(), tc.getDescription() );
                }
                else {
                    System.err.printf("  FAIL %10s - %s\n", tc.getTestName(), tc.getDescription());
                    System.err.printf("    got valid: (%s)\n", actualValid);
                    System.err.printf("    expected valid: (%s)\n", expectedValid);
                    // the following will throw
                    Assert.assertEquals(actualValid, expectedValid, "result not as expected");
                }
            }
        }
        System.out.println("=========================================================");
    }

}
