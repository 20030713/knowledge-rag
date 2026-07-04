package com.rag.knowledge.vector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rag.knowledge.config.EmbeddingModelProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Primary
@Service
public class OpenAiCompatibleTextEmbeddingService implements TextEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleTextEmbeddingService.class);

    private final EmbeddingModelProperties properties;
    private final HashingTextEmbeddingService fallback;
    private final RestClient restClient;

    public OpenAiCompatibleTextEmbeddingService(
            EmbeddingModelProperties properties,
            HashingTextEmbeddingService fallback
    ) {
        this.properties = properties;
        this.fallback = fallback;
        this.restClient = buildRestClient();
    }

    @Override
    public double[] embed(String text) {
        if (!modelAvailable()) {
            return fallback.embed(text);
        }
        try {
            EmbeddingResponse response = restClient.post()
                    .uri(properties.safeEndpointPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new EmbeddingRequest(properties.getModel(), text == null ? "" : text, properties.safeDimensions()))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            double[] vector = extractVector(response);
            return vector.length == 0 ? fallback.embed(text) : normalize(vector);
        } catch (Exception exception) {
            log.warn("Embedding model call failed, fallback to local hashing. model={}, baseUrl={}",
                    properties.getModel(), properties.safeBaseUrl(), exception);
            return fallback.embed(text);
        }
    }

    @Override
    public int dimensions() {
        return modelAvailable() ? properties.safeDimensions() : fallback.dimensions();
    }

    @Override
    public boolean modelAvailable() {
        return properties.available();
    }

    @Override
    public String modelName() {
        return modelAvailable() ? properties.getModel() : fallback.modelName();
    }

    @Override
    public String modelNamespace() {
        if (!modelAvailable()) {
            return fallback.modelNamespace();
        }
        return "openai-compatible-embedding:" + properties.safeBaseUrl() + ":" + properties.getModel()
                + ":" + properties.safeDimensions();
    }

    private RestClient buildRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.safeTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.safeTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(properties.safeBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private double[] extractVector(EmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return new double[0];
        }
        EmbeddingData first = response.data().getFirst();
        if (first == null || first.embedding() == null || first.embedding().isEmpty()) {
            return new double[0];
        }
        double[] vector = new double[first.embedding().size()];
        for (int index = 0; index < first.embedding().size(); index++) {
            vector[index] = first.embedding().get(index);
        }
        return vector;
    }

    private double[] normalize(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return vector;
        }
        double norm = Math.sqrt(sum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
        return vector;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record EmbeddingRequest(
            String model,
            String input,
            Integer dimensions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(
            List<EmbeddingData> data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(
            List<Double> embedding
    ) {
    }
}
