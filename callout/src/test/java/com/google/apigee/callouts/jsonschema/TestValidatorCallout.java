// Copyright © 2018 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.apigee.callouts.jsonschema;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.io.CharSource;
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
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestValidatorCallout {
  private static final String testDataDir = "src/test/resources/test-data";

  MessageContext msgCtxt;
  String messageContent;
  Message message;
  ExecutionContext exeCtxt;

  @BeforeMethod()
  public void testSetup1() {
    messageContent = null;

    msgCtxt =
        new MockUp<MessageContext>() {
          private Map<String,Object> variables;

          public void $init() {
            variables = new HashMap<String,Object>();
          }

          @Mock()
          public Object getVariable(final String name) {
            if (variables == null) {
              variables = new HashMap<String,Object>();
            }
            return variables.get(name);
          }

          @Mock()
          public boolean setVariable(final String name, final Object value) {
            if (variables == null) {
              variables = new HashMap<String,Object>();
            }
            variables.put(name, value);
            return true;
          }

          @Mock()
          public boolean removeVariable(final String name) {
            if (variables == null) {
              variables = new HashMap<String,Object>();
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

    exeCtxt = new MockUp<ExecutionContext>() {}.getMockInstance();

    message =
        new MockUp<Message>() {
          @Mock()
          public InputStream getContentAsStream() {
            if (messageContent == null) {
              return new ByteArrayInputStream(new byte[0]);
            }
            return new ByteArrayInputStream(messageContent.getBytes(StandardCharsets.UTF_8));
          }
        }.getMockInstance();
  }

  @DataProvider(name = "batch1")
  public static Object[][] getDataForBatch1() throws IOException, IllegalStateException, Exception {

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
    int c = 0;
    ArrayList<TestCase> list = new ArrayList<TestCase>();
    for (File file : files) {
      String name = file.getName();
      if (name.startsWith("test") && name.endsWith(".json")) {
        TestCase tc = mapper.readValue(file, TestCase.class);
        tc.setTestName(name.substring(0, name.length() - 5));
        if (tc.getExpected() == null) {
          throw new Exception("invalid test case");
        }
        if (Strings.isNullOrEmpty(tc.getDescription())) {
          tc.setDescription("-no description-");
        }
        for (String key : tc.getContext().keySet()) {
          String value = tc.getContext().get(key);
          if (value.startsWith("file://")) {
            name = value.substring(7);
            Path path = Paths.get(testDataDir, name);
            if (!Files.exists(path)) {
              throw new IOException("file(" + name + ") for context(" + key + ") not found");
            }

            // replace the content with what is read from the file
            CharSource source =
                com.google.common.io.Files.asCharSource(path.toFile(), StandardCharsets.UTF_8);
            tc.getContext().put(key, source.read());
          }
        }
        list.add(tc);
      }
    }

    // OMG!!  Seriously? Is this the easiest way to generate a 2-d array?
    int n = list.size();
    Object[][] data = new Object[n][];
    for (int i = 0; i < data.length; i++) {
      data[i] = new Object[] {list.get(i)};
    }
    return data;
  }

  @Test
  public void testDataProviders() throws IOException, Exception {
    Assert.assertTrue(getDataForBatch1().length > 0);
  }

  private void reportResult(TestCase tc, boolean isFailed, String actual, String expected) {
    System.err.printf(
        "%s %-48s  %s\n", isFailed ? "FAIL" : "PASS", tc.getTestName(), tc.getDescription());
    if (isFailed) {
      System.err.printf("    got: %s\n", actual);
      System.err.printf("    expected: %s\n", expected);
    }
    Assert.assertEquals(actual, expected, "result not as expected");
    System.out.println("=====================================================================");
  }

  @Test(dataProvider = "batch1")
  public void test2_Configs(TestCase tc) {
    ValidatorCallout callout = new ValidatorCallout(tc.getProperties()); // properties
    for (String key : tc.getContext().keySet()) {
      msgCtxt.setVariable(key, tc.getContext().get(key));
    }

    if (tc.getContext().containsKey("message.content")) {
      // This will be provided as a readable stream, by the msgCtxt.
      messageContent = tc.getContext().get("message.content");
    }

    // execute and retrieve output
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    ExecutionResult expectedResult =
        ("success".equals((String) tc.getExpected().get("result")))
            ? ExecutionResult.SUCCESS
            : ExecutionResult.ABORT;

    // check result and output
    boolean isFailed = (expectedResult != actualResult);
    if (isFailed) {
      reportResult(tc, isFailed, actualResult.toString(), expectedResult.toString());
      return;
    }

    if (expectedResult == ExecutionResult.ABORT) {
      String expectedError = (String) tc.getExpected().get("error");
      Assert.assertFalse(Strings.isNullOrEmpty(expectedError), "expected error");
      String actualError = msgCtxt.getVariable("jsv_error");
      isFailed = !expectedError.equals(actualError);
      reportResult(tc, isFailed, (String) actualError, (String) expectedError);
      return;
    }

    // completed successfully as expected.  Now let's see if it was valid
    String expectedError = (String) tc.getExpected().get("error");
    if (!Strings.isNullOrEmpty(expectedError)) {
      String actualError = msgCtxt.getVariable("jsv_error");
      isFailed = !expectedError.equals(actualError);
      reportResult(tc, isFailed, (String) actualError, (String) expectedError);
      return;
    }

    boolean expectedValid = (boolean) tc.getExpected().get("valid");
    boolean actualValid = msgCtxt.getVariable("jsv_valid");
    isFailed = (expectedValid != actualValid);
    reportResult(
        tc, isFailed, new Boolean(actualValid).toString(), new Boolean(expectedValid).toString());
  }
}
