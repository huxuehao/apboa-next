package com.hxh.apboa.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.agent.mapper.AgentChatKeyMapper;
import com.hxh.apboa.agent.service.AgentChatKeyService;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.entity.AgentChatKey;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.util.RedisKeyBuilder;
import com.hxh.apboa.common.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 描述：智能体对话Key服务实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class AgentChatKeyServiceImpl extends ServiceImpl<AgentChatKeyMapper, AgentChatKey> implements AgentChatKeyService {
    private final AgentDefinitionService agentDefinitionService;
    private final RedisUtils redisUtils;

    /**
     * 空值缓存标识，用于防止缓存穿透
     */
    private static final String NULL_VALUE_PLACEHOLDER = "__NULL__";

    /**
     * 缓存过期时间（天）
     */
    private static final long CACHE_EXPIRE_DAYS = 7;

    /**
     * 空值缓存过期时间（分钟）
     */
    private static final long NULL_CACHE_EXPIRE_MINUTES = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String getChatKey(Long agentId, boolean refresh) {
        if (agentId == null || agentId <= 0) {
            throw new IllegalArgumentException("智能体ID不能为空且必须大于0");
        }

        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null) {
            throw new RuntimeException("智能体不存在，ID: " + agentId);
        }

        String agentCode = agent.getAgentCode();

        synchronized (agentCode.intern()) {
            AgentChatKey existingKey = getOne(
                    new LambdaQueryWrapper<AgentChatKey>().eq(AgentChatKey::getAgentCode, agentCode)
                    , false);

            // 不需要刷新且有现有Key
            if (!refresh && existingKey != null) {
                return existingKey.getChatKey();
            }

            // 需要生成新的Key
            String newChatKey = generateRandomKey(agentId);

            // 先删除在保存
            remove(new LambdaQueryWrapper<AgentChatKey>().eq(AgentChatKey::getAgentCode, agentCode));
            AgentChatKey item = new AgentChatKey();
            item.setAgentCode(agentCode);
            item.setChatKey(newChatKey);
            if (existingKey != null) {
                // 嵌入身份密钥随记录删除重建继承——轮换 chatKey 不弄丢业务方手里的 embedSecret
                item.setEmbedSecret(existingKey.getEmbedSecret());
                item.setEmbedSecretPrev(existingKey.getEmbedSecretPrev());
            }
            save(item);

            // 删除旧的 ChatKey -> AgentCode 的映射关系
            if (existingKey != null) {
                String oldRedisKey = buildChatKeyRedisKey(existingKey.getChatKey());
                redisUtils.delete(oldRedisKey);
            }

            // 将 ChatKey -> AgentCode 的映射关系存储到Redis
            String redisKey = buildChatKeyRedisKey(newChatKey);
            redisUtils.setEx(redisKey, agentCode, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);

            return newChatKey;
        }
    }

    @Override
    public String getAgentCodeByChatKey(String chatKey) {
        if (chatKey == null || chatKey.isEmpty()) {
            return null;
        }

        String redisKey = buildChatKeyRedisKey(chatKey);

        // 1. 先从Redis缓存获取
        String cachedValue = redisUtils.get(redisKey);
        if (cachedValue != null) {
            // 空值缓存直接返回null
            if (NULL_VALUE_PLACEHOLDER.equals(cachedValue)) {
                return null;
            }
            return cachedValue;
        }

        // 2. 缓存未命中，从数据库查询
        AgentChatKey agentChatKey = getOne(
                new LambdaQueryWrapper<AgentChatKey>().eq(AgentChatKey::getChatKey, chatKey),
                false);

        if (agentChatKey != null) {
            // 查询到数据，回填Redis缓存
            String agentCode = agentChatKey.getAgentCode();
            redisUtils.setEx(redisKey, agentCode, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            return agentCode;
        } else {
            // 3. 缓存穿透防护：缓存空值，设置较短的过期时间
            redisUtils.setEx(redisKey, NULL_VALUE_PLACEHOLDER, NULL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            return null;
        }
    }

    @Override
    public Long getAgentIdByChatKey(String chatKey) {
        String agentCode = getAgentCodeByChatKey(chatKey);
        if (agentCode == null || agentCode.isEmpty()) {
            throw new RuntimeException("无效的 " + chatKey);
        }

        AgentDefinition agentDefinition = agentDefinitionService.getOne(
                new LambdaQueryWrapper<AgentDefinition>().eq(AgentDefinition::getAgentCode, agentCode),
                false);

        if (agentDefinition == null) {
            throw new RuntimeException("为找到对应的智能体");
        }

        return agentDefinition.getId();
    }

    @Override
    public String getEmbedSecret(Long agentId) {
        AgentChatKey key = getByAgentId(agentId);
        return key != null ? key.getEmbedSecret() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rotateEmbedSecret(Long agentId) {
        // chatKey 不存在时先生成（getChatKey 内部幂等）
        getChatKey(agentId, false);

        AgentChatKey key = getByAgentId(agentId);
        if (key == null) {
            throw new RuntimeException("chatKey 生成失败，无法配置嵌入身份密钥");
        }

        // 48 字节随机 → base64url（>256bit，满足 HMAC-SHA256 密钥强度）
        byte[] randomBytes = new byte[48];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String newSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // 旧密钥转 prev 双活（轮换观察期新旧同时可验签）
        lambdaUpdate()
                .eq(AgentChatKey::getChatKey, key.getChatKey())
                .set(AgentChatKey::getEmbedSecret, newSecret)
                .set(AgentChatKey::getEmbedSecretPrev, key.getEmbedSecret())
                .update();
        return newSecret;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableEmbedSecret(Long agentId) {
        AgentChatKey key = getByAgentId(agentId);
        if (key == null) {
            return;
        }
        // 双密钥一并置空（lambdaUpdate 显式 set null，绕过 MP updateById 的 NOT_NULL 策略）
        lambdaUpdate()
                .eq(AgentChatKey::getChatKey, key.getChatKey())
                .set(AgentChatKey::getEmbedSecret, null)
                .set(AgentChatKey::getEmbedSecretPrev, null)
                .update();
    }

    private AgentChatKey getByAgentId(Long agentId) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null) {
            return null;
        }
        return getOne(
                new LambdaQueryWrapper<AgentChatKey>().eq(AgentChatKey::getAgentCode, agent.getAgentCode()),
                false);
    }

    /**
     * 构建Redis Key
     *
     * @param chatKey 对话Key
     * @return Redis Key
     */
    private String buildChatKeyRedisKey(String chatKey) {
        return RedisKeyBuilder.globalKey("chatkey:" + chatKey);
    }

    public static String generateRandomKey(long snowflakeId) {
        // 获取随机UUID的低64位作为随机部分
        UUID randomUuid = UUID.randomUUID();
        // 组合：高64位用雪花ID，低64位用UUID的随机部分
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        // 雪花ID占高64位
        bb.putLong(snowflakeId);
        // 随机部分占低64位
        bb.putLong(randomUuid.getLeastSignificantBits());

        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bb.array());
    }
}
