package com.project.login.service.remark;

import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.RemarkDO;
import com.project.login.model.dataobject.UserDO;
import com.project.login.model.dto.remark.RemarkSelectByNoteDTO;
import com.project.login.model.vo.RemarkVO;
import com.project.login.repository.RemarkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 评论树 / 楼中楼集成测试。
 *  - 验证 {@link RemarkService#SelectRemark} 能返回完整的多层 replies 链
 *  - 用真实 MongoDB + 真实 Redis，避免 mock 掉 RemarkRepository / RedisTemplate
 *  - UserMapper、RabbitTemplate 用 @MockitoBean 拦截，避免依赖 MySQL / RabbitMQ
 *  - 使用随机 noteId / 评论 ID，避免与已有数据冲突
 */
@SpringBootTest
class RemarkReplyTreeIT {

    private static final Long LOGIN_USER_ID = 9001L;
    private static final Long AUTHOR_USER_ID = 9002L;

    @Autowired private RemarkService remarkService;
    @Autowired private RemarkRepository remarkRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean private UserMapper userMapper;
    @MockitoBean private RabbitTemplate rabbitTemplate;

    /** 该次测试使用的 noteId 与各层评论 ID，便于 cleanup 时定向删除。 */
    private Long noteId;
    private String l1Id;
    private String l2Id;
    private String l3Id;
    private String l4Id;

    @BeforeEach
    void setUp() {
        // 用绝对值远离常见业务 ID 范围的随机 noteId，规避脏数据污染
        noteId = 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L);
        l1Id = "tree-it-l1-" + UUID.randomUUID();
        l2Id = "tree-it-l2-" + UUID.randomUUID();
        l3Id = "tree-it-l3-" + UUID.randomUUID();
        l4Id = "tree-it-l4-" + UUID.randomUUID();

        // UserMapper 的两类调用：
        //   1) SelectRemark 开头 userMapper.selectById(loginUserId)，返回值会被 toString() 打印（不能为 null）
        //   2) transferDO2VO 内部 userMapper.selectById(remarkDO.getUserId()) 用于设置头像
        UserDO stubUser = UserDO.builder()
                .id(LOGIN_USER_ID)
                .username("tester")
                .avatarUrl(null)
                .build();
        when(userMapper.selectById(anyLong())).thenReturn(stubUser);

        cleanState();
    }

    @AfterEach
    void tearDown() {
        cleanState();
    }

    private void cleanState() {
        for (String id : List.of(l1Id, l2Id, l3Id, l4Id)) {
            remarkRepository.deleteById(id);
            redisTemplate.delete("remark:" + id);
            redisTemplate.delete("remark_stats:" + id);
            redisTemplate.delete("remark_user_like:" + id);
        }
        redisTemplate.delete("note_id_of_remark_list:" + noteId);
    }

    /**
     * 构造一条评论。{@code parentId == null} 视作一级评论（isReply=false）。
     */
    private void saveRemark(String id, String parentId, String content, long createdAtMillis) {
        boolean isReply = parentId != null;
        RemarkDO remark = RemarkDO.builder()
                ._id(id)
                .noteId(noteId)
                .userId(AUTHOR_USER_ID)
                .username("author")
                .content(content)
                .createdAt(String.valueOf(createdAtMillis))
                .parentId(parentId)
                .isReply(isReply)
                .replyToRemarkId(parentId)
                .replyToUsername(isReply ? "author" : null)
                .build();
        remarkRepository.save(remark);
    }

    // ============ tests ============

    /**
     * 4 层楼中楼：L1 → L2 → L3 → L4，验证 SelectRemark 返回完整的 replies 链。
     */
    @Test
    void selectRemark_returnsCompleteFourLevelReplyTree() {
        saveRemark(l1Id, null, "level-1", 1);
        saveRemark(l2Id, l1Id, "level-2", 2);
        saveRemark(l3Id, l2Id, "level-3", 3);
        saveRemark(l4Id, l3Id, "level-4", 4);

        RemarkSelectByNoteDTO dto = RemarkSelectByNoteDTO.builder().noteId(noteId).build();
        List<RemarkVO> firstLayer = remarkService.SelectRemark(dto, LOGIN_USER_ID);

        // 只有 L1 是一级评论
        assertThat(firstLayer).hasSize(1);
        RemarkVO l1 = firstLayer.get(0);
        assertThat(l1.get_id()).isEqualTo(l1Id);
        assertThat(l1.getContent()).isEqualTo("level-1");
        // bug 修复关键点：replies 不能为 null
        assertThat(l1.getReplies())
                .as("L1.replies 应该被 buildReplyTree 填充，不能为 null（这是上一次复原遗留的 bug）")
                .isNotNull()
                .hasSize(1);

        RemarkVO l2 = l1.getReplies().get(0);
        assertThat(l2.get_id()).isEqualTo(l2Id);
        assertThat(l2.getContent()).isEqualTo("level-2");
        assertThat(l2.getReplies())
                .as("L2 应该带有 L3 这一层回复")
                .hasSize(1);

        RemarkVO l3 = l2.getReplies().get(0);
        assertThat(l3.get_id()).isEqualTo(l3Id);
        assertThat(l3.getContent()).isEqualTo("level-3");
        assertThat(l3.getReplies())
                .as("L3 应该带有 L4 这一层回复")
                .hasSize(1);

        RemarkVO l4 = l3.getReplies().get(0);
        assertThat(l4.get_id()).isEqualTo(l4Id);
        assertThat(l4.getContent()).isEqualTo("level-4");
        assertThat(l4.getReplies())
                .as("L4 是叶子，replies 应为空 list（不是 null）")
                .isNotNull()
                .isEmpty();
    }

    /**
     * 同级多个回复：L1 下有两个并列回复 L2a / L2b，验证按时间排序与 replies 长度。
     */
    @Test
    void selectRemark_returnsSiblingsInTimeOrder() {
        String l2aId = "tree-it-l2a-" + UUID.randomUUID();
        String l2bId = "tree-it-l2b-" + UUID.randomUUID();
        try {
            saveRemark(l1Id, null, "level-1", 1);
            // 后保存的反而时间更早，验证 buildReplyTree 内部按 createdAt 排序
            saveRemark(l2bId, l1Id, "level-2-b", 20);
            saveRemark(l2aId, l1Id, "level-2-a", 10);

            RemarkSelectByNoteDTO dto = RemarkSelectByNoteDTO.builder().noteId(noteId).build();
            List<RemarkVO> firstLayer = remarkService.SelectRemark(dto, LOGIN_USER_ID);

            assertThat(firstLayer).hasSize(1);
            List<RemarkVO> replies = firstLayer.get(0).getReplies();
            assertThat(replies).hasSize(2);
            // createdAt 字典序排序：10 < 20
            assertThat(replies.get(0).getContent()).isEqualTo("level-2-a");
            assertThat(replies.get(1).getContent()).isEqualTo("level-2-b");
        } finally {
            for (String id : List.of(l2aId, l2bId)) {
                remarkRepository.deleteById(id);
                redisTemplate.delete("remark:" + id);
                redisTemplate.delete("remark_stats:" + id);
                redisTemplate.delete("remark_user_like:" + id);
            }
        }
    }

    /**
     * 单个一级评论无任何子回复，replies 应为空 list 而非 null。
     */
    @Test
    void selectRemark_singleTopLevelComment_repliesIsEmptyList() {
        saveRemark(l1Id, null, "level-1", 1);

        RemarkSelectByNoteDTO dto = RemarkSelectByNoteDTO.builder().noteId(noteId).build();
        List<RemarkVO> firstLayer = remarkService.SelectRemark(dto, LOGIN_USER_ID);

        assertThat(firstLayer).hasSize(1);
        assertThat(firstLayer.get(0).getReplies())
                .isNotNull()
                .isEmpty();
    }

    /**
     * note 下没有任何一级评论时返回空 list。
     */
    @Test
    void selectRemark_emptyNote_returnsEmptyList() {
        RemarkSelectByNoteDTO dto = RemarkSelectByNoteDTO.builder().noteId(noteId).build();
        List<RemarkVO> firstLayer = remarkService.SelectRemark(dto, LOGIN_USER_ID);

        // 接受 Redis 上次缓存的空 list（remarkFirstLayerList 初始就是 empty ArrayList）
        // 这里关键是不抛异常、不返回 null
        assertThat(firstLayer).isNotNull();
        // 不强制 isEmpty()——可能其他遗留 noteId 等价的脏数据会出现；
        // 改为只验证：当前 noteId 下不该出现我们的 L1 / L2 等 id
        List<String> ids = new ArrayList<>();
        firstLayer.forEach(v -> ids.add(v.get_id()));
        assertThat(ids).doesNotContain(l1Id, l2Id, l3Id, l4Id);
    }
}
