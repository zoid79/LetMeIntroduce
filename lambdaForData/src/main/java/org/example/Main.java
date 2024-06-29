package org.example;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;

public class Main implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final String OTHER_LAMBDA_FUNCTION_NAME = "Lambda_ARN_or_funcURL"; // 호출할 다른 Lambda 함수 이름
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		LambdaLogger logger = context.getLogger();

		try {
			// JSON 데이터 파싱
			String requestBody = request.getBody();
			Map<String, Object> requestData = objectMapper.readValue(requestBody, Map.class);

			String jobRole = (String) requestData.get("jobRole");
			String company = (String) requestData.get("company");

			List<Map<String, Object>> questionList = (List<Map<String, Object>>) requestData.get("questionList");

			// 성공적인 응답 생성
			APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
			for (Map<String, Object> questionData : questionList) {
				String question = (String) questionData.get("question");
				List<String> addressList = (List<String>) questionData.get("address");
				String otherData = (String) questionData.get("otherData");

				// 각 데이터 처리 예시
				logger.log("Question: " + question);
				logger.log("Address List: " + addressList);
				logger.log("Other Data: " + otherData);

				// 여기서 readUrl() 함수 등을 호출하여 필요한 처리 수행
				String readMeContent = readUrl(addressList.get(0), logger);
				// 예를 들어 addressList의 첫 번째 주소에 대한 처리

				// 다른 Lambda API에 데이터 전달
				String response = callOtherLambdaApi(readMeContent, question, otherData, company, jobRole, logger);
				logger.log("Response from other Lambda: " + response);

				responseEvent.setStatusCode(200);
				responseEvent.setBody(response);
			}

			return responseEvent;

		} catch (Exception e) {
			logger.log("Error handling request: " + e.getMessage());
			// 오류 발생 시 적절한 응답 처리
			APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
			responseEvent.setStatusCode(500); // Internal Server Error
			responseEvent.setBody("Error handling request: " + e.getMessage());
			return responseEvent;
		}
	}

	private String readUrl(String repoUrl, LambdaLogger logger) {
		String newUrl = repoUrl + "?plain=1";
		StringBuilder contentBuilder = new StringBuilder();
		try {
			Document doc = Jsoup.connect(newUrl).get();
			Elements elements = doc.getElementsByClass("react-file-line html-div");
			for (Element element : elements) {
				contentBuilder.append(element.text()).append("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return contentBuilder.toString();
	}

	private String callOtherLambdaApi(String requestData, String question, String otherData, String company, String jobRole,
		LambdaLogger logger) {
		AWSLambda lambda = AWSLambdaClientBuilder.defaultClient();
		logger.log(lambda.toString());

		// JSON 객체로 데이터 병합
		Map<String, String> payloadMap = new HashMap<>();
		payloadMap.put("requestData", requestData);
		payloadMap.put("question", question);
		payloadMap.put("otherData", otherData);
		payloadMap.put("company", company);
		payloadMap.put("jobRole", jobRole);

		try {
			// JSON 문자열로 변환
			String payloadJson = objectMapper.writeValueAsString(payloadMap);
			logger.log("Payload JSON: " + payloadJson);

			// 호출할 다른 Lambda 함수의 InvokeRequest 생성
			InvokeRequest invokeRequest = new InvokeRequest()
				.withFunctionName(OTHER_LAMBDA_FUNCTION_NAME)
				.withPayload(payloadJson);

			// 다른 Lambda 함수 호출
			InvokeResult invokeResult = lambda.invoke(invokeRequest);
			logger.log("InvokeResult: " + invokeResult.toString());

			// 호출 결과를 문자열로 변환하여 반환
			return new String(invokeResult.getPayload().array());
		} catch (Exception e) {
			logger.log("Error calling Lambda function: " + e.getMessage());
			// 예외 처리 필요에 따라 적절히 처리
			return null; // 예시에서는 null을 반환하도록 처리
		}
	}

}
