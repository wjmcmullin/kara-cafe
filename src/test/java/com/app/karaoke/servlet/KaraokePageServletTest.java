package com.app.karaoke.servlet;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class KaraokePageServletTest {

    private KaraokePageServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        servlet = new KaraokePageServlet();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        dispatcher = mock(RequestDispatcher.class);
    }

    @Test
    void forwardsToRoomHtmlWhenPathIsRoom() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/karaoke-room/room");
        when(request.getRequestDispatcher("/karaoke-room/room.html")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(request).getRequestDispatcher("/karaoke-room/room.html");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void forwardsToSearchHtmlWhenPathEndsWithSearch() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/karaoke-room/search");
        when(request.getRequestDispatcher("/karaoke-room/search.html")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(request).getRequestDispatcher("/karaoke-room/search.html");
        verify(dispatcher).forward(request, response);
    }
}
