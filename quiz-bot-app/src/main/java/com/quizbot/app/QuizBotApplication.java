package com.quizbot.app;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@ComponentScan(basePackages = "com.quizbot")
@EnableScheduling
@PropertySource("classpath:application.properties")
public class QuizBotApplication {

    public static void main(String[] args) throws Exception {
        // Start Spring Context
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(QuizBotApplication.class);
        context.registerShutdownHook();

        // Manual Jetty Start for Spring 7 MVC
        Server server = new Server(8080);
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        server.setHandler(handler);

        // Link DispatcherServlet to the Spring Context
        DispatcherServlet dispatcherServlet = new DispatcherServlet();
        AnnotationConfigWebApplicationContext webContext = new AnnotationConfigWebApplicationContext();
        webContext.setParent(context);
        webContext.register(QuizBotApplication.class);
        dispatcherServlet.setApplicationContext(webContext);

        handler.addServlet(new ServletHolder(dispatcherServlet), "/");

        server.start();
        System.out.println("Quiz Bot [Spring 7 + Java 25] is running on http://localhost:8080");
        server.join();
    }

    @Bean
    public Executor applicationTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
