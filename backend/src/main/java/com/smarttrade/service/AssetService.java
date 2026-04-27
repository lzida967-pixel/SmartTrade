package com.smarttrade.service;

import com.smarttrade.entity.UserAssetSnapshot;
import com.smarttrade.vo.UserAssetVO;

import java.util.List;

/**
 * 用户资产聚合服务：当前资产计算、每日快照、收益曲线
 */
public interface AssetService {

    /**
     * 计算当前用户的实时资产（含持仓最新价、市值、浮盈亏）。
     * 这是给 /user/asset 接口用的。
     */
    UserAssetVO buildCurrentAsset(Long userId);

    /**
     * 拍一个用户的"今日"快照：upsert 到 user_asset_snapshot 表（按 user_id + snapshot_date 唯一）
     */
    UserAssetSnapshot upsertTodaySnapshot(Long userId);

    /**
     * 给所有用户拍今日快照。返回成功数量。
     */
    int upsertAllUsersTodaySnapshot();

    /**
     * 拉取最近 N 天的资产曲线（按日期升序）。
     * 如果某天没有快照（比如周末），不会自动补点；前端按需自己处理。
     */
    List<UserAssetSnapshot> getCurve(Long userId, int days);
}
