package com.project.login.service.remark;

import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.UserDO;
import com.project.login.model.dto.remark.RemarkSelectByNoteDTO;
import com.project.login.repository.RemarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
class RemarkRepositoryQueryIT {

    @Autowired
    private RemarkRepository remarkRepository;

    @Autowired
    private RemarkService remarkService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        when(userMapper.selectById(anyLong())).thenReturn(
                UserDO.builder().id(104L).username("cs_user_001").build()
        );
    }

    @Test
    void findRepliesForNote332FirstComment() {
        var replies = remarkRepository.findRemarksByParentIdAndIsReplyTrue("6a4a9f4872f1a7433f8eb07b");
        assertThat(replies).isNotEmpty();
    }

    @Test
    void findByRemarkIdForReplyWorks() {
        assertThat(remarkRepository.findByRemarkId("6a4a9f4872f1a7433f8eb07c")).isPresent();
    }

    @Test
    void selectRemarkForNote332IncludesReplies() {
        RemarkSelectByNoteDTO dto = new RemarkSelectByNoteDTO();
        dto.setNoteId(332L);
        var list = remarkService.SelectRemark(dto, 104L);
        var first = list.stream()
                .filter(v -> "6a4a9f4872f1a7433f8eb07b".equals(v.get_id()))
                .findFirst()
                .orElseThrow();
        assertThat(first.getReplies()).isNotEmpty();
    }
}
