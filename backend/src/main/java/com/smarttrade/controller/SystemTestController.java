package com.smarttrade.controller;

import com.smarttrade.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemTestController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("Pong, SmartTrade Backend is running.");
    }

}