package com.smarttrade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDTO {

    @NotBlank(message = "注册账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;
}