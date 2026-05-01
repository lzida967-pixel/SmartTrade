package com.smarttrade.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Python 预测服务返回结构（对应 prediction-service 的 PredictionResponse）。
 *
 * Python 端用 snake_case，这里用 @JsonAlias 仅影响反序列化（读入），
 * 序列化（写给前端）仍输出 Java 字段名（camelCase），与项目其他接口风格一致。
 */
@Data
public class PredictionDTO {

    private String code;

    @JsonAlias("as_of_date")
    private LocalDate asOfDate;

    @JsonAlias("forward_days")
    private Integer forwardDays;

    private Double threshold;

    /** 三类概率：bullish / neutral / bearish */
    private Map<String, Double> proba;

    /** 0=看多 1=震荡 2=看空 */
    private Integer label;

    @JsonAlias("label_name")
    private String labelName;

    private Double confidence;

    @JsonAlias("top_features")
    private List<FeatureContrib> topFeatures;

    @JsonAlias("model_version")
    private String modelVersion;

    private String disclaimer;

    @Data
    public static class FeatureContrib {
        private String name;
        private Double value;
        private Integer importance;
    }
}
