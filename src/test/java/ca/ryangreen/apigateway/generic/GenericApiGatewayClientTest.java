package ca.ryangreen.apigateway.generic;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.http.AmazonHttpClient;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.http.apache.client.impl.SdkHttpClient;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;

public class GenericApiGatewayClientTest {
    private GenericApiGatewayClient client;
    private SdkHttpClient mockClient;
    @Before
    public void setUp() throws IOException {
        AWSCredentialsProvider credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("foo", "bar"));

        mockClient = Mockito.mock(SdkHttpClient.class);
        HttpResponse resp = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream("test payload".getBytes()));
        runMockito(resp,entity);

        ClientConfiguration clientConfig = new ClientConfiguration();

        client = new GenericApiGatewayClientBuilder()
                .withClientConfiguration(clientConfig)
                .withCredentials(credentials)
                .withEndpoint("https://foobar.execute-api.us-east-1.amazonaws.com")
                .withRegion(Region.getRegion(Regions.fromName("us-east-1")))
                .withApiKey("12345")
                .withHttpClient(new AmazonHttpClient(clientConfig, mockClient, null))
                .build();
    }

    private void runMockito(HttpResponse resp, BasicHttpEntity entity) throws IOException {
        resp.setEntity(entity);
        Mockito.doReturn(resp).when(mockClient).execute(any(HttpUriRequest.class), any(HttpContext.class));
    }

    @Test
    public void testExecuteHappy() throws IOException {
        Map<String, String> headers = getHeaders();
        AmazonWebServiceResponse<GenericApiGatewayResponse> response = getResponse(headers);

        runResponseChecks(response);

        Mockito.verify(mockClient, times(1)).execute(argThat(new LambdaMatcher<>(
                        x -> (x.getMethod().equals("POST")
                                && x.getFirstHeader("Account-Id").getValue().equals("fubar")
                                && x.getFirstHeader("x-api-key").getValue().equals("12345")
                                && x.getFirstHeader("Authorization").getValue().startsWith("AWS4")
                                && x.getURI().toString().equals("https://foobar.execute-api.us-east-1.amazonaws.com/test/orders")))),
                any(HttpContext.class));
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Account-Id", "fubar");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private void runResponseChecks(AmazonWebServiceResponse<GenericApiGatewayResponse> response) {
        assertEquals("Wrong response body", "test payload", response.getResult().getBody());
        assertEquals("Wrong response status", 200, response.getResult().getHttpResponse().getStatusCode());
    }

    private AmazonWebServiceResponse<GenericApiGatewayResponse> getResponse(Map<String, String> headers) {
        return client.execute(
                getRequestBuilder(headers).build());
    }

    @Test
    public void testExecuteHappyParameters() throws IOException {
        Map<String, String> headers = getHeaders();
        Map<String,List<String>> parameters = new HashMap<>();
        parameters.put("MyParam", Collections.singletonList("MyParamValue"));
        AmazonWebServiceResponse<GenericApiGatewayResponse> response = client.execute(
                getRequestBuilder(headers).withParameters(parameters).build());

        runResponseChecks(response);

        Mockito.verify(mockClient, times(1)).execute(argThat(new LambdaMatcher<>(
                        x -> (x.getMethod().equals("POST")
                                && x.getFirstHeader("Account-Id").getValue().equals("fubar")
                                && x.getFirstHeader("x-api-key").getValue().equals("12345")
                                && x.getFirstHeader("Authorization").getValue().startsWith("AWS4")
                                && x.getURI().toString().equals("https://foobar.execute-api.us-east-1.amazonaws.com/test/orders?MyParam=MyParamValue")))),
                any(HttpContext.class));
    }

    private GenericApiGatewayRequestBuilder getRequestBuilder(Map<String, String> headers) {
        return getBaseRequestBuilder()
                .withHeaders(headers);
    }

    private GenericApiGatewayRequestBuilder getBaseRequestBuilder() {
        return new GenericApiGatewayRequestBuilder()
                .withBody(new ByteArrayInputStream("test request".getBytes()))
                .withHttpMethod(HttpMethodName.POST)
                .withResourcePath("/test/orders");
    }

    @Test
    public void testExecuteNoApiKeyNoCreds() throws IOException {
        client = new GenericApiGatewayClientBuilder()
                .withEndpoint("https://foobar.execute-api.us-east-1.amazonaws.com")
                .withRegion(Region.getRegion(Regions.fromName("us-east-1")))
                .withClientConfiguration(new ClientConfiguration())
                .withHttpClient(new AmazonHttpClient(new ClientConfiguration(), mockClient, null))
                .build();

        AmazonWebServiceResponse<GenericApiGatewayResponse> response = client.execute(
                getBaseRequestBuilder().build());

        runResponseChecks(response);

        Mockito.verify(mockClient, times(1)).execute(argThat(new LambdaMatcher<>(
                        x -> (x.getMethod().equals("POST")
                                && x.getFirstHeader("x-api-key") == null
                                && x.getFirstHeader("Authorization") == null
                                && x.getURI().toString().equals("https://foobar.execute-api.us-east-1.amazonaws.com/test/orders")))),
                any(HttpContext.class));
    }

    @Test
    public void testExecuteNon2XxException() throws IOException {
        HttpResponse resp = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 404, "Not found"));
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream("{\"message\" : \"error payload\"}".getBytes()));
        runMockito(resp,entity);

        Map<String, String> headers = getHeaders();

        try {
            getResponse(headers);
            Assert.fail("Expected exception");
        } catch (GenericApiGatewayException e) {
            assertEquals("Wrong status code", 404, e.getStatusCode());
            assertEquals("Wrong exception message", "{\"message\":\"error payload\"}", e.getErrorMessage());
        }
    }


}
