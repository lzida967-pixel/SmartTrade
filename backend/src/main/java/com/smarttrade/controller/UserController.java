package com.smarttrade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smarttrade.common.Result;
import com.smarttrade.dto.LoginDTO;
import com.smarttrade.dto.RegisterDTO;
import com.smarttrade.dto.UpdateProfileDTO;
import com.smarttrade.entity.User;
import com.smarttrade.entity.UserPosition;
import com.smarttrade.service.UserPositionService;
import com.smarttrade.service.UserService;
import com.smarttrade.utils.UserContext;
import com.smarttrade.vo.UserAssetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserPositionService userPositionService;

    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {    
        String token = userService.login(loginDTO);
        return Result.success(token, "登录成功");
    }

    @PostMapping("/register")
    public Result<String> register(@Validated @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success("注册成功");
    }

    /**
     * 获取当前登录人的账户资产信息
     */
    @GetMapping("/info")
    public Result<User> getUserInfo() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        if(user != null) {
            // 脱敏：不将密码返回给前端
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * 修改用户基本信息 (例如昵称、头像)
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileDTO dto) {
        Long userId = UserContext.getUserId();
        User user = new User();
        user.setId(userId);
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        // mybatis-plus 将自动忽略为 null 的字段，只更新非空字段
        userService.updateById(user);
        return Result.success(null, "信息更新成功");
    }

    /**
     * 用户资产聚合：账户资金 + 持仓列表 + 持仓市值
     */
    @GetMapping("/asset")
    public Result<UserAssetVO> getAsset() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        List<UserPosition> positions = userPositionService.list(
                new LambdaQueryWrapper<UserPosition>()
                        .eq(UserPosition::getUserId, userId)
                        .gt(UserPosition::getQuantity, 0)
        );

        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal floatingProfit = BigDecimal.ZERO;
        for (UserPosition p : positions) {
            if (p.getMarketValue() != null) {
                marketValue = marketValue.add(p.getMarketValue());
            }
            if (p.getFloatingProfit() != null) {
                floatingProfit = floatingProfit.add(p.getFloatingProfit());
            }
        }

        UserAssetVO vo = new UserAssetVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvailableFunds(nullToZero(user.getAvailableFunds()));
        vo.setFrozenFunds(nullToZero(user.getFrozenFunds()));
        vo.setMarketValue(marketValue);
        vo.setFloatingProfit(floatingProfit);
        vo.setTotalAssets(vo.getAvailableFunds().add(vo.getFrozenFunds()).add(marketValue));
        vo.setPositions(positions);
        return Result.success(vo);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
