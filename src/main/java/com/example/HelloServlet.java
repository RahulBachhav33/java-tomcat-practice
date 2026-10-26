package com.example;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;

@WebServlet(urlPatterns = {"/", "/health"})
public class HelloServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String path = request.getRequestURI();
        String appEnv = System.getenv().getOrDefault("APP_ENV", "local");
        int status = "/health".equals(path) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_OK;
        response.setStatus(status);

        response.getWriter().printf(
            "{\"application\":\"java-tomcat-practice\",\"environment\":\"%s\",\"path\":\"%s\",\"status\":\"UP\",\"timestamp\":\"%s\"}%n",
            escape(appEnv), escape(path), Instant.now().toString()
        );
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
