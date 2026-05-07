package com.smarttrade.vo;

import lombok.Data;

/**
 * 单个 key 前缀的缓存命中率统计结果
 */
@Data
public class CacheMetricVO {

    /** key 前缀，如 "stock:quote:*" */
    private String prefix;

    /** 命中次数 */
    private long hits;

    /** 未命中次数（穿透到源站 / DB） */
    private long misses;

    /** 总访问次数 = hits + misses */
    private long total;

    /** 命中率 [0, 1]，前端按 % 渲染 */
    private double hitRate;
}
