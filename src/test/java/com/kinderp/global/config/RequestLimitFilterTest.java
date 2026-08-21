package com.kinderp.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("fast")
class RequestLimitFilterTest {

    private final RequestLimitFilter filter = new RequestLimitFilter(new RequestLimitProperties());

    @Test
    void rejectsRequestBodyAboveConfiguredLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, successfulChain());

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void rejectsTooManyParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (int index = 0; index < 101; index++) {
            request.addParameter("p" + index, "value");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, successfulChain());

        assertThat(response.getStatus()).isEqualTo(413);
    }

    private FilterChain successfulChain() {
        return (request, response) -> ((jakarta.servlet.http.HttpServletResponse) response)
                .getWriter().write("ok");
    }
}
