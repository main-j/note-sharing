package com.project.login.service.remark;

import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.RemarkDO;
import com.project.login.model.dataobject.UserDO;
import com.project.login.model.dto.remark.RemarkSelectByNoteDTO;
import com.project.login.model.vo.RemarkVO;
import com.project.login.repository.RemarkLikeByUsersRepository;
import com.project.login.repository.RemarkLikeCountRepository;
import com.project.login.repository.RemarkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 复现并守护"点赞后刷新页面显示未点赞"这一线上 bug 的端到端集成测试。
 *
 * <p>覆盖的核心路径：
 * <ol>
 *   <li>{@link RemarkService#likeRemark(String, Long)} 把 userId 写入 Redis user_like Set</li>
 *   <li>{@link RemarkService#SelectRemark} 内部 {@code transferDO2VO} 用 {@code isMember} 回读</li>
 *   <li>VO 中 {@code likedOrNot} 应当与点赞动作一致</li>
 * </ol>
 *
 * <p>这条链路在 RemarkServiceIT 中被绕过（直接断言 isMember），所以这里专门补齐。
 */
@SpringBootTest
class RemarkLikeReadbackIT {

    private static final Long LOGIN_USER_ID = 7001L;
    private static final Long OTHER_USER_ID = 7002L;
    private static final Long AUTHOR_USER_ID = 7003L;

    @Autowired private RemarkService remarkService;
    @Autowired private RemarkRepository remarkRepository;
    @Autowired private RemarkLikeCountRepository countRepo;
    @Autowired private RemarkLikeByUsersRepository usersRepo;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean private UserMapper userMapper;
    @MockitoBean private RabbitTemplate rabbitTemplate;

    private Long noteId;
    private String remarkId;

    @BeforeEach
    void setUp() {
        noteId = 7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L);
        remarkId = "readback-it-" + UUID.randomUUID();

        UserDO stubUser = UserDO.builder()
                .id(LOGIN_USER_ID)
                .username("login-user")
                .build();
        when(userMapper.selectById(anyLong())).thenReturn(stubUser);

        cleanState();
        saveTopLevelRemark();
    }

    @AfterEach
    void tearDown() {
        cleanState();
    }

    private void cleanState() {
        remarkRepository.deleteById(remarkId);
        countRepo.deleteById(remarkId);
        usersRepo.deleteById(remarkId);
        redisTemplate.delete("remark:" + remarkId);
        redisTemplate.delete("remark_stats:" + remarkId);
        redisTemplate.delete("remark_user_like:" + remarkId);
        redisTemplate.delete("note_id_of_remark_list:" + noteId);
    }

    private void saveTopLevelRemark() {
        RemarkDO remark = RemarkDO.builder()
                ._id(remarkId)
                .noteId(noteId)
                .userId(AUTHOR_USER_ID)
                .username("author")
                .content("hello world")
                .createdAt("1")
                .parentId(null)
                .isReply(false)
                .build();
        remarkRepository.save(remark);
    }

    private RemarkVO selectFirstRemark() {
        RemarkSelectByNoteDTO dto = RemarkSelectByNoteDTO.builder().noteId(noteId).build();
        List<RemarkVO> list = remarkService.SelectRemark(dto, LOGIN_USER_ID);
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    // ============ 主复现用例 ============

    /**
     * 点赞 → 立即查询，应当看到 likedOrNot == true，count == 1。
     * 用户报告的现象：点赞后刷新页面变成未点赞。
     */
    @Test
    void likeThenSelect_currentUserSeesLikedTrue() {
        assertThat(remarkService.likeRemark(remarkId, LOGIN_USER_ID)).isTrue();

        RemarkVO vo = selectFirstRemark();

        assertThat(vo.getLikeCount()).isEqualTo(1L);
        assertThat(vo.getLikedOrNot())
                .as("点赞后刷新页面，当前用户应该看到 likedOrNot=true")
                .isTrue();
    }

    /**
     * 取消点赞 → 查询，应看到 likedOrNot == false，count == 0。
     */
    @Test
    void likeThenCancelThenSelect_currentUserSeesLikedFalse() {
        remarkService.likeRemark(remarkId, LOGIN_USER_ID);
        assertThat(remarkService.cancelLikeRemark(remarkId, LOGIN_USER_ID)).isTrue();

        RemarkVO vo = selectFirstRemark();

        assertThat(vo.getLikeCount()).isZero();
        assertThat(vo.getLikedOrNot()).isFalse();
    }

    /**
     * 用户 A 点赞，用户 B 查询：likedOrNot 对 B 来说应当是 false，count = 1。
     */
    @Test
    void likeByUserA_otherUserBSeesLikedFalseButCountOne() {
        remarkService.likeRemark(remarkId, OTHER_USER_ID);

        // 切换登录用户 ID 为 LOGIN_USER_ID（默认 stub）
        RemarkVO vo = selectFirstRemark();

        assertThat(vo.getLikeCount()).isEqualTo(1L);
        assertThat(vo.getLikedOrNot())
                .as("点赞的是 OTHER_USER，登录的是 LOGIN_USER，likedOrNot 应当为 false")
                .isFalse();
    }

    /**
     * 关键守护：HTTP 响应中 likedOrNot 的 JSON 字段名必须稳定。
     * <p>
     * 字段在 VO 里写成 {@code LikedOrNot}（首字母大写），Lombok 生成 {@code getLikedOrNot()}。
     * Jackson 按 JavaBeans 规则把属性名推导为 {@code likedOrNot}（首字母小写），
     * 但这是 Jackson/Lombok 版本相关的行为，一旦前后端约定漂移就会出现"点赞后刷新显示未点赞"的现象。
     * 这里把 JSON 字段名固化下来，回归时立刻能发现。
     */
    @Test
    void likedOrNot_jsonFieldNameIsStable() throws Exception {
        remarkService.likeRemark(remarkId, LOGIN_USER_ID);
        RemarkVO vo = selectFirstRemark();

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = om.writeValueAsString(vo);

        // 前端 (CommentItem.vue / NoteDetailView.vue) 与后端的约定字段名 = likedOrNot（首字母小写）。
        // 历史上曾因 VO 字段写成 LikedOrNot 大写、前端取 comment.LikedOrNot 同时大写 但
        // Jackson 序列化为 likedOrNot 小写，导致"点赞后刷新页面显示未点赞"。
        // 这里把契约钉死，任何方向漂移（VO 改名 / 注解新增 / Jackson 配置改变）都会被测试拦下。
        assertThat(json)
                .as("JSON 必须用首字母小写的 likedOrNot；不允许写成 LikedOrNot，否则前端 comment.likedOrNot 取不到值")
                .contains("\"likedOrNot\":true")
                .doesNotContain("\"LikedOrNot\":");
    }

    /**
     * 模拟"先点赞写入 Redis，consumer 落盘后 deleteIfCold 清掉 Redis，
     * 然后下次读触发 initStatsIfNeeded 从 DB 回填" 的链路。
     * 回填后 likedOrNot 应当仍然 = true。
     */
    @Test
    void likeThenRedisEvicted_readThroughRestoresLikedTrue() {
        remarkService.likeRemark(remarkId, LOGIN_USER_ID);

        // 模拟 consumer 已落盘到 DB 并清除了 Redis（与 deleteIfCold 等价）
        // 1) 把 Redis 当前状态写回 Mongo（手动模拟 consumer 写库）
        com.project.login.model.dataobject.RemarkCountDO countDO =
                com.project.login.model.dataobject.RemarkCountDO.builder()
                        .remarkId(remarkId)
                        .remarkLikeCount(1L)
                        .build();
        countRepo.save(countDO);

        java.util.HashSet<Long> userSet = new java.util.HashSet<>();
        userSet.add(LOGIN_USER_ID);
        com.project.login.model.dataobject.RemarkLikeByUsersDO usersDO =
                com.project.login.model.dataobject.RemarkLikeByUsersDO.builder()
                        .remarkId(remarkId)
                        .userList(userSet)
                        .build();
        usersRepo.save(usersDO);

        // 2) 清掉 Redis 模拟 deleteIfCold
        redisTemplate.delete("remark_stats:" + remarkId);
        redisTemplate.delete("remark_user_like:" + remarkId);

        // 3) 此时读 → 应触发 initStatsIfNeeded 从 DB 回填
        RemarkVO vo = selectFirstRemark();

        assertThat(vo.getLikeCount())
                .as("read-through 应该把 count 从 Mongo 加载回 Redis，再返回给前端")
                .isEqualTo(1L);
        assertThat(vo.getLikedOrNot())
                .as("read-through 应该把 user_like 从 Mongo 回填，前端应看到已点赞")
                .isTrue();
    }
}
