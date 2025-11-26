package com.project.login.service.admin;

import com.project.login.model.request.admin.UserAdminUpdateRequest;
import com.project.login.model.entity.UserEntity;
import com.project.login.model.dataobject.UserDO;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final NoteSpaceMapper noteSpaceMapper;

    @Transactional
    public UserEntity updateUser(Long id, UserAdminUpdateRequest req) {
        UserDO userDO = userMapper.selectById(id);
        if (userDO == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserEntity user = toEntity(userDO);

        if (req.getUsername() != null && !Objects.equals(user.getUsername(), req.getUsername())) {
            user.setUsername(req.getUsername());
        }

        if (req.getStudentNumber() != null && !Objects.equals(user.getStudentNumber(), req.getStudentNumber())) {
            UserEntity snUser = userMapper.selectByStudentNumber(req.getStudentNumber());
            if (snUser != null && !Objects.equals(snUser.getId(), id)) {
                throw new IllegalArgumentException("学号已存在");
            }
            user.setStudentNumber(req.getStudentNumber());
        }

        if (req.getEmail() != null && !Objects.equals(user.getEmail(), req.getEmail())) {
            UserEntity mailUser = userMapper.selectByEmail(req.getEmail());
            if (mailUser != null && !Objects.equals(mailUser.getId(), id)) {
                throw new IllegalArgumentException("邮箱已存在");
            }
            user.setEmail(req.getEmail());
        }

        userMapper.updateUser(user);
        return toEntity(userMapper.selectById(id));
    }

    @Transactional
    public void deleteUser(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        noteSpaceMapper.selectByUser(id).forEach(space -> noteSpaceMapper.deleteNoteSpace(space.getId()));
        userMapper.deleteUserById(id);
    }

    private UserEntity toEntity(UserDO d) {
        if (d == null) return null;
        UserEntity e = new UserEntity();
        e.setId(d.getId());
        e.setUsername(d.getUsername());
        e.setStudentNumber(d.getStudentNumber());
        e.setEmail(d.getEmail());
        e.setPassword_hash(d.getPassword_hash());
        e.setEnabled(d.isEnabled());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}
