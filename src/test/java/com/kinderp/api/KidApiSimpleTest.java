package com.kinderp.api;

import com.kinderp.common.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("원생 API 간단 테스트")
@Tag("integration")
class KidApiSimpleTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(username = "parent@test.com", roles = {"PARENT"})
    @DisplayName("원생 단건 조회 - 성공")
    void getKid_Success() throws Exception {
        mockMvc.perform(get("/api/v1/kids/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
