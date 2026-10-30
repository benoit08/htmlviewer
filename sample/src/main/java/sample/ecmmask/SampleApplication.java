package sample.ecmmask;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.webresources.DirResourceSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import sample.ecmmask.xvarm.InzentXvarmClient;
import sample.ecmmask.xvarm.LocalXvarmClient;
import sample.ecmmask.xvarm.XvarmClient;
import sample.ecmmask.xvarm.XvarmProperties;

/**
 * 샘플 실행용 설정 (EDMS 로 옮기지 않음 — EDMS 에서는 기존 XML 설정 / 톰캣 배포가 이 역할을 한다).
 * 실행: sample 폴더에서 mvnw spring-boot:run → http://localhost:8080
 *
 * 프런트 산출물(../ecm-mask)은 복사하지 않고 그대로 연결한다 — 샘플이 곧 산출물 검증.
 *  - ecmMask.jsp, ecmMask.body.jspf → /WEB-INF/views/ecm/   (EDMS 의 JSP 위치에 해당)
 *  - css, js, lib/pdfjs             → /resources/ecm-mask/  (ecmMask.jsp 의 res 경로)
 */
@SpringBootApplication
@EnableConfigurationProperties(XvarmProperties.class)
public class SampleApplication implements WebMvcConfigurer {

    @Value("${ecm-mask.dir}")
    private String ecmMaskDir;

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    private String ecmMaskPath() {
        return new File(ecmMaskDir).getAbsoluteFile().toURI().normalize().getPath();
    }

    /** /resources/ecm-mask/** → ../ecm-mask 폴더 (EDMS 의 &lt;mvc:resources&gt; 설정에 해당) */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/ecm-mask/**")
                .addResourceLocations(new File(ecmMaskDir).getAbsoluteFile().toURI().normalize().toString());
    }

    /** ../ecm-mask 폴더를 /WEB-INF/views/ecm 으로 붙인다 (JSP 를 복사하지 않기 위함) */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mountEcmMaskViews() {
        final String path = new File(ecmMaskDir).getAbsolutePath();
        return new WebServerFactoryCustomizer<TomcatServletWebServerFactory>() {
            public void customize(TomcatServletWebServerFactory factory) {
                factory.addContextCustomizers(new TomcatContextCustomizer() {
                    public void customize(final Context context) {
                        context.addLifecycleListener(new LifecycleListener() {
                            public void lifecycleEvent(LifecycleEvent event) {
                                if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                                    context.getResources().addPreResources(
                                            new DirResourceSet(context.getResources(), "/WEB-INF/views/ecm", path, "/"));
                                }
                            }
                        });
                    }
                });
            }
        };
    }

    /** xvarm.mode=inzent 면 실제 엔진, 아니면 로컬 폴더 저장 */
    @Bean(name = "xvarmClient")
    public XvarmClient xvarmClient(XvarmProperties prop) {
        if ("inzent".equals(prop.getMode())) {
            return new InzentXvarmClient(prop);
        }
        return new LocalXvarmClient(prop.getLocalDir());
    }
}
