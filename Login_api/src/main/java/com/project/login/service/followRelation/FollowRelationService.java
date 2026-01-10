package com.project.login.service.followRelation;

import com.project.login.convert.FollowRelationConvert;
import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.FollowRelationDO;
import com.project.login.model.dataobject.FollowUser;
import com.project.login.model.vo.followRelation.GetFollowersVO;
import com.project.login.model.vo.followRelation.GetFollowingsVO;
import com.project.login.repository.FollowRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowRelationService {

    private final FollowRelationRepository repository;

    private final FollowRelationConvert convert;

    private final UserMapper userMapper;

    public GetFollowingsVO getFollowings(Long userId) {
        FollowRelationDO doc = repository.findByUserId(userId);
        if (doc == null) {
            return new GetFollowingsVO(); // 空结果
        }
        return convert.toFollowingsVO(doc);
    }


    public GetFollowersVO getFollowers(Long userId) {
        FollowRelationDO doc = repository.findByUserId(userId);
        if (doc == null) {
            return new GetFollowersVO(); // 空结果
        }
        return convert.toFollowersVO(doc);
    }


    public boolean isFollowing(Long userId, Long targetUserId) {
        FollowRelationDO doc = repository.findByUserId(userId);
        if (doc == null || doc.getFollowing() == null) return false;

        return doc.getFollowing().stream()
                .anyMatch(u -> u.getUserId().equals(targetUserId));
    }


    public FollowRelationDO initFollowRelation(Long userId) {
        FollowRelationDO doc = repository.findByUserId(userId);
        if (doc == null) {
            doc = new FollowRelationDO();
            doc.setUserId(userId);
            doc.setFollowing(new ArrayList<>());
            doc.setFollowers(new ArrayList<>());
            doc.setUpdateTime(LocalDateTime.now());
            repository.save(doc);
        }
        return doc;
    }


    public int follow(Long userId, Long targetUserId) {

        if (userId.equals(targetUserId)) {
            return -1; // 不能关注自己
        }

        // 校验用户是否真实存在
        if (userMapper.selectById(userId) == null ||
                userMapper.selectById(targetUserId) == null) {
            return -3; // 用户不存在
        }

        // 初始化（不存在就创建）
        FollowRelationDO me = initFollowRelation(userId);
        FollowRelationDO target = initFollowRelation(targetUserId);

        // 防止 following 为空（双保险）
        if (me.getFollowing() == null) {
            me.setFollowing(new ArrayList<>());
        }

        // 判断是否已关注
        boolean alreadyFollowed = me.getFollowing().stream()
                .anyMatch(u -> u.getUserId().equals(targetUserId));
        if (alreadyFollowed) {
            return -2; // 已关注
        }

        // 执行关注
        me.getFollowing().add(new FollowUser(targetUserId, LocalDateTime.now()));
        target.getFollowers().add(new FollowUser(userId, LocalDateTime.now()));

        me.setUpdateTime(LocalDateTime.now());
        target.setUpdateTime(LocalDateTime.now());

        repository.save(me);
        repository.save(target);

        return 0;
    }



    public int unfollow(Long userId, Long targetUserId) {

        if (userId.equals(targetUserId)) {
            return -2; // 不能取关自己
        }

        FollowRelationDO me = repository.findByUserId(userId);
        FollowRelationDO target = repository.findByUserId(targetUserId);

        if (me == null || target == null) {
            return -2; // 用户不存在或数据异常
        }

        if (me.getFollowing() == null || me.getFollowing().isEmpty()) {
            return -1; // 从未关注过任何人
        }

        // 判断是否真的关注过
        boolean existed = me.getFollowing().stream()
                .anyMatch(u -> u.getUserId().equals(targetUserId));

        if (!existed) {
            return -1; // 本来就没关注
        }

        // 执行取关
        me.getFollowing().removeIf(u -> u.getUserId().equals(targetUserId));

        if (target.getFollowers() != null) {
            target.getFollowers().removeIf(u -> u.getUserId().equals(userId));
        }

        me.setUpdateTime(LocalDateTime.now());
        target.setUpdateTime(LocalDateTime.now());

        repository.save(me);
        repository.save(target);

        return 0; // 取关成功
    }


}
