package org.example;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Main implements RequestHandler<Map<String, String>, String> {

	private final String API_URL= "chatGPT_URL";
	private final OkHttpClient client= new OkHttpClient.Builder().connectTimeout(90, TimeUnit.SECONDS)
		.readTimeout(90, TimeUnit.SECONDS)
		.writeTimeout(90, TimeUnit.SECONDS)
		.build();
	private final ObjectMapper objectMapper=new ObjectMapper();

	@Override
	public String handleRequest(Map<String, String> input, Context context) {
		LambdaLogger a = context.getLogger();
		String requestData = input.get("requestData");//readme
		String question = input.get("question");//문항
		String otherData = input.get("otherData");//기타
		String company = input.get("company");
		String jobRole = input.get("jobRole");
		a.log(requestData);
		a.log(question);
		a.log(otherData);
		a.log(company);
		a.log(jobRole);

		Map<String, Object> systemMessage = new HashMap<>();
		systemMessage.put("role", "system");
		systemMessage.put("content", jobRole);

		Map<String, Object> userMessage = new HashMap<>();
		userMessage.put("role", "user");

		userMessage.put("content", company+" 이런 회사에 지원할거야"+jobRole+"이 직무에 지원할 거니까"+requestData+"을 요약한 걸 조금만 참고해서 "+question+"에 맞게 자기소개서를 써줘"+" 그리고 "+otherData+"를 강조해줘");

		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("model", "gpt-4");
		requestBody.put("messages", List.of(systemMessage, userMessage));
		RequestBody body;
		try {
			body = RequestBody.create(
				objectMapper.writeValueAsString(requestBody),
				MediaType.parse("application/json")
			);
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize request body", e);
		}

		Request mmlRequest = new Request.Builder()
			.url(API_URL)
			.header("Authorization", "Bearer " + "chatGPT_API_KEY")
			.post(body)
			.build();

		try (Response response = client.newCall(mmlRequest).execute()) {
			if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
			String responseBody = response.body().string();
			Map<String, Object> result = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
			List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
			Map<String, Object> firstChoice = choices.get(0);
			Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
			return (String) message.get("content");
		} catch (IOException e) {
			throw new RuntimeException("Failed to get response from API", e);
		}
	}
}
