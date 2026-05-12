package com.liveklass.enrollment.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("X-Trace-Id 헤더가 없으면 traceId 생성 — 체인 동안 MDC 에 존재, 응답 헤더 세팅, 종료 후 MDC 클리어")
    void generatesTraceIdWhenAbsent() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(req.getHeader(TraceIdFilter.HEADER)).willReturn(null);
        String[] duringChain = new String[1];
        doAnswer(inv -> {
            duringChain[0] = MDC.get(TraceIdFilter.TRACE_ID);
            return null;
        }).when(chain).doFilter(req, res);

        filter.doFilterInternal(req, res, chain);

        assertThat(duringChain[0]).isNotBlank();
        verify(res).setHeader(TraceIdFilter.HEADER, duringChain[0]);
        verify(chain).doFilter(req, res);
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("X-Trace-Id 헤더가 오면 그 값을 그대로 사용")
    void reusesProvidedTraceId() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(req.getHeader(TraceIdFilter.HEADER)).willReturn("incoming-trace");
        String[] duringChain = new String[1];
        doAnswer(inv -> {
            duringChain[0] = MDC.get(TraceIdFilter.TRACE_ID);
            return null;
        }).when(chain).doFilter(req, res);

        filter.doFilterInternal(req, res, chain);

        assertThat(duringChain[0]).isEqualTo("incoming-trace");
        verify(res).setHeader(TraceIdFilter.HEADER, "incoming-trace");
    }
}
