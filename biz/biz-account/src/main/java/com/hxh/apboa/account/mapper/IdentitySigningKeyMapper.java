package com.hxh.apboa.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.IdentitySigningKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述：身份断言签名密钥数据访问层
 *
 * @author vaulka
 **/
@Mapper
public interface IdentitySigningKeyMapper extends BaseMapper<IdentitySigningKey> {
}
