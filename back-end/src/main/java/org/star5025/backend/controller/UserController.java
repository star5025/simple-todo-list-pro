package org.star5025.backend.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.star5025.backend.dto.UserDTO;
import org.star5025.backend.dto.UserUpdateDTO;
import org.star5025.backend.dto.ValidationGroup;
import org.star5025.backend.entity.User;
import org.star5025.backend.properties.JwtProperties;
import org.star5025.backend.result.Result;
import org.star5025.backend.service.UserService;
import org.star5025.backend.utils.JwtUtil;
import org.star5025.backend.vo.UserProfileVO;
import org.star5025.backend.vo.UserVO;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Api(tags = "用户相关接口")
@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    @ApiOperation("用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Validated({ValidationGroup.Group1.class, ValidationGroup.Group2.class,
            ValidationGroup.Group3.class}) @RequestBody UserDTO userDTO) {
        userService.register(userDTO);
        return Result.success();
    }

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody @Valid UserDTO userDTO) {
        User user = userService.login(userDTO);
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        String token = JwtUtil.createJWT(jwtProperties.getSecretKey(), jwtProperties.getTtl(), claims);
        return Result.success(UserVO.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .token(token)
                .build());
    }

    @PostMapping("/logout")
    @ApiOperation("用户登出")
    public Result<Void> logout() {
        return Result.success();
    }

    @ApiOperation("根据用户Id查找用户")
    @GetMapping("/{userId}")
    public Result<UserProfileVO> getUserById(@PathVariable Long userId) {
        User user = userService.getUserById(userId);
        return Result.success(UserProfileVO.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .createTime(user.getCreateTime())
                .taskCount(user.getTaskCount())
                .build());
    }

    @ApiOperation("修改用户信息")
    @PutMapping
    public Result<Void> updateUser(@RequestBody UserUpdateDTO userUpdateDTO) {
        userService.updateUser(userUpdateDTO);
        return Result.success();
    }

    @ApiOperation("删除用户")
    @DeleteMapping
    public Result<Void> deleteUser(@ModelAttribute User user) {
        userService.deleteUser(user);
        return Result.success();
    }
}
