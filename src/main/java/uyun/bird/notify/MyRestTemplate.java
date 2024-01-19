package uyun.bird.notify;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * @Author: hsy
 * @Description: 禁用证书验证
 * @Date: 2024/1/19 12:43
 */
public class MyRestTemplate {
    public static RestTemplate createRestTemplate() {
        // 创建一个信任所有证书的 SSLContext
        SSLContext sslContext;
        try {
            sslContext = SSLContextBuilder
                    .create()
                    .loadTrustMaterial(new TrustSelfSignedStrategy())
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }

        // 配置 RestTemplate 使用自定义的 ClientHttpRequestFactory
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory(sslContext));
        return restTemplate;
    }

    private static ClientHttpRequestFactory createRequestFactory(SSLContext sslContext) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(
                HttpClientBuilder.create()
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .build()
        );
        return requestFactory;
    }
}
