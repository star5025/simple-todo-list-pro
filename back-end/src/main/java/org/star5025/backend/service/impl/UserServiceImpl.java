package org.star5025.backend.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.context.BaseContext;
import org.star5025.backend.dto.UserDTO;
import org.star5025.backend.dto.UserUpdateDTO;
import org.star5025.backend.entity.User;
import org.star5025.backend.mapper.UserMapper;
import org.star5025.backend.service.PasswordService;
import org.star5025.backend.service.UserService;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordService passwordService;

    @Override
    public void register(UserDTO userDTO) {
        validateUserName(userDTO.getUserName());
        User user = User.builder()
                .userName(userDTO.getUserName())
                .userPassword(passwordService.encode(userDTO.getUserPassword()))
                .createTime(LocalDateTime.now(java.time.ZoneOffset.UTC))
                .taskCount(0)
                .build();
        try {
            userMapper.register(user);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
    }

    @Override
    public User login(UserDTO userDTO) {
        if (userDTO.getUserName() == null || userDTO.getUserName().isBlank()
                || userDTO.getUserPassword() == null || userDTO.getUserPassword().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        User user = userMapper.getByUserName(userDTO.getUserName());
        if (user == null || !passwordService.matches(userDTO.getUserPassword(), user.getUserPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String originalHash = user.getUserPassword();
        if (passwordService.isLegacyMd5(originalHash)) {
            String upgradedHash = passwordService.encodeLegacy(userDTO.getUserPassword());
            if (userMapper.updatePasswordIfUnchanged(user.getUserId(), originalHash, upgradedHash) == 1) {
                user.setUserPassword(upgradedHash);
            } else {
                // Another login may have migrated it, or a password change/deletion won the race.
                user = userMapper.getByUserId(user.getUserId());
                if (user == null || passwordService.isLegacyMd5(user.getUserPassword())
                        || !passwordService.matches(userDTO.getUserPassword(), user.getUserPassword())) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号状态已更改，请重新登录");
                }
            }
        }
        return user;
    }

    @Override
    public User getUserById(Long userId) {
        requireSelf(userId);
        User user = userMapper.getByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @Override
    public void updateUser(UserUpdateDTO update) {
        User user = getUserById(update.getUserId());
        boolean changePassword = update.getUserPassword() != null;
        if (changePassword != (update.getOldPassword() != null)
                || (changePassword && update.getOldPassword().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "修改密码必须提供旧密码和新密码");
        }
        if (!changePassword && update.getUserName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未提供修改内容");
        }
        if (update.getUserName() != null) {
            validateUserName(update.getUserName());
            user.setUserName(update.getUserName());
        }
        String originalHash = user.getUserPassword();
        if (changePassword) {
            if (!passwordService.matches(update.getOldPassword(), originalHash)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "原密码不正确");
            }
            user.setUserPassword(passwordService.encode(update.getUserPassword()));
        }
        int changed;
        try {
            changed = changePassword
                    ? userMapper.updateWithPassword(user, originalHash)
                    : userMapper.updateName(user.getUserId(), user.getUserName());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        if (changed == 0) {
            if (!userMapper.existsById(user.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户状态已更改，请重试");
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteUser(User user) {
        requireSelf(user.getUserId());
        userMapper.deleteSchedules(user.getUserId());
        userMapper.deleteTasks(user.getUserId());
        if (userMapper.delete(user) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    @Override
    public void incrementTaskCount(Long userId, int count) {
        requireSelf(userId);
        userMapper.incrementTaskCount(userId, count);
    }

    @Override
    public boolean verifyPassword(Long userId, String password) {
        return passwordService.matches(password, getUserById(userId).getUserPassword());
    }

    private void requireSelf(Long userId) {
        Long currentId = BaseContext.requireCurrentId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户ID不能为空");
        }
        if (!currentId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他用户");
        }
    }

    private void validateUserName(String name) {
        if (name == null || !name.matches("\\S{3,16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名须为3-16个非空白字符");
        }
    }
}
