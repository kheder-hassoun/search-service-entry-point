package web.server.IDF_web_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("forward:/admin.html");
        // Optional: keep / to index.html explicitly too
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}