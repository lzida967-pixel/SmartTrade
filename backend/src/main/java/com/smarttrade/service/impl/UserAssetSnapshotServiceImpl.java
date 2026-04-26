package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.UserAssetSnapshot;
import com.smarttrade.mapper.UserAssetSnapshotMapper;
import com.smarttrade.service.UserAssetSnapshotService;
import org.springframework.stereotype.Service;

@Service
public class UserAssetSnapshotServiceImpl extends ServiceImpl<UserAssetSnapshotMapper, UserAssetSnapshot>
        implements UserAssetSnapshotService {
}
