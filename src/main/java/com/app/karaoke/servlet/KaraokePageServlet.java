package com.app.karaoke.servlet;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves the extensionless room and search URLs by forwarding to the existing
 * HTML files.
 */
@WebServlet(urlPatterns = {"/karaoke-room/room", "/karaoke-room/search"})
public class KaraokePageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String requestUri = req.getRequestURI();
        String target;

        if (requestUri.endsWith("/search")) {
            target = "/karaoke-room/search.html";
        } else {
            target = "/karaoke-room/room.html";
        }

        req.getRequestDispatcher(target).forward(req, resp);
    }
}
