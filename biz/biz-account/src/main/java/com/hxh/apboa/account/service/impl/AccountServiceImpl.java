package com.hxh.apboa.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hxh.apboa.account.mapper.AccountMapper;
import com.hxh.apboa.account.mapper.TenantMapper;
import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.account.service.AccountTenantService;
import com.hxh.apboa.account.service.TenantInitService;
import com.hxh.apboa.account.service.TenantJoinRequestService;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.dto.*;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.entity.TenantJoinRequest;
import com.hxh.apboa.common.enums.TenantJoinRequestStatus;
import com.hxh.apboa.common.enums.TenantRole;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.exception.NotAuthException;
import com.hxh.apboa.common.util.*;
import com.hxh.apboa.params.core.ParamsAdapter;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 账号Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private final RedisUtils redisUtils;
    private final ParamsAdapter paramsAdapter;
    private final AccountTenantService accountTenantService;
    private final TenantJoinRequestService tenantJoinRequestService;
    private final TenantMapper tenantMapper;
    private final TenantInitService tenantInitService;
    private final AgentDefinitionService agentDefinitionService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public LoginResponse login(LoginRequest request) {
        if (FuncUtils.isEmpty(request.getUsername()) || FuncUtils.isEmpty(request.getPassword())) {
            throw new RuntimeException("用户名和密码不能为空");
        }

        // 查询用户（支持用户名或邮箱登录）
        Account account = this.lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(Account::getUsername, request.getUsername())
                        .or()
                        .eq(Account::getEmail, request.getUsername())
                )
                .one();

        if (account == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 检查用户是否被禁用
        if (Boolean.FALSE.equals(account.getEnabled())) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        // 验证密码
        String salt = account.getId().toString();
        if (!passwordMatches(request.getPassword(), salt, account.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 查询账号关联的租户（已审批通过且启用的）
        List<AccountTenant> memberships = accountTenantService.listByAccountId(account.getId());
        List<AccountTenant> activeMemberships = new ArrayList<>();
        for (AccountTenant m : memberships) {
            if (Boolean.TRUE.equals(m.getEnabled())) {
                activeMemberships.add(m);
            }
        }

        // 查询该用户所有 PENDING 状态的加入申请
        List<TenantJoinRequest> pendingRequests = listAccountPendingRequests(account.getId());

        // 无有效租户成员 — 检查是否有待审批申请
        if (activeMemberships.isEmpty()) {
            if (!pendingRequests.isEmpty()) {
                // 有待审批申请，返回 blocked 状态
                List<PendingApprovalInfo> pendingList = new ArrayList<>();
                for (TenantJoinRequest pr : pendingRequests) {
                    Tenant t = tenantMapper.selectById(pr.getTenantId());
                    pendingList.add(PendingApprovalInfo.builder()
                            .requestId(pr.getId())
                            .tenantId(pr.getTenantId())
                            .tenantName(t != null ? t.getName() : null)
                            .tenantCode(t != null ? t.getCode() : null)
                            .status(pr.getStatus().name())
                            .createdAt(pr.getCreatedAt())
                            .build());
                }
                return LoginResponse.builder()
                        .blocked(true)
                        .pendingApprovals(pendingList)
                        .build();
            }
            throw new RuntimeException("当前账号未加入任何组织，请先注册");
        }

        // 用户已指定租户（选租户后重新登录），验证成员身份并直接登录
        if (request.getTenantId() != null) {
            for (AccountTenant m : activeMemberships) {
                if (m.getTenantId().equals(request.getTenantId())) {
                    Tenant tenant = tenantMapper.selectById(m.getTenantId());
                    if (tenant != null) {
                        account.setLastTenantId(tenant.getId());
                        this.updateById(account);
                        TenantUtils.setCurrentTenant(tenant.getId(), tenant.getCode());
                        return generateTokenResponse(account, m.getTenantId(), tenant.getCode(),
                                m.getRole(), tenant.getName());
                    }
                }
            }
            throw new RuntimeException("您不是该租户的成员");
        }

        // 仅有一个租户，自动选择
        if (activeMemberships.size() == 1) {
            AccountTenant membership = activeMemberships.getFirst();
            Tenant tenant = tenantMapper.selectById(membership.getTenantId());
            if (tenant != null) {
                TenantUtils.setCurrentTenant(tenant.getId(), tenant.getCode());
                return generateTokenResponse(account, membership.getTenantId(), tenant.getCode(),
                        membership.getRole(), tenant.getName());
            }
        }

        // 多个租户 — 尝试使用上次登录的租户自动选择
        if (account.getRememberLastTenant() && account.getLastTenantId() != null) {
            for (AccountTenant m : activeMemberships) {
                if (m.getTenantId().equals(account.getLastTenantId())) {
                    Tenant tenant = tenantMapper.selectById(m.getTenantId());
                    if (tenant != null) {
                        TenantUtils.setCurrentTenant(tenant.getId(), tenant.getCode());
                        return generateTokenResponse(account, m.getTenantId(), tenant.getCode(),
                                m.getRole(), tenant.getName());
                    }
                }
            }
        }

        // 多个租户且无法自动选择，需要用户选择
        List<UserDetail.TenantInfo> tenantInfoList = new ArrayList<>();
        for (AccountTenant membership : activeMemberships) {
            Tenant tenant = tenantMapper.selectById(membership.getTenantId());
            tenantInfoList.add(new UserDetail.TenantInfo(
                    membership.getTenantId(),
                    tenant != null ? tenant.getCode() : null,
                    tenant != null ? tenant.getName() : null,
                    membership.getRole().name()
            ));
        }

        return LoginResponse.builder()
                .needSelectTenant(true)
                .tenants(tenantInfoList)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean register(RegisterRequest request) {
        // 参数校验
        validateRegisterRequest(request);

        // 注册模式互斥校验
        if (Boolean.TRUE.equals(request.getCreateTenant()) && request.getJoinTenantId() != null) {
            throw new RuntimeException("不能同时创建组织和加入已有组织");
        }
        if (!Boolean.TRUE.equals(request.getCreateTenant()) && request.getJoinTenantId() == null) {
            throw new RuntimeException("请选择创建自己的组织或加入已有组织");
        }

        // 检查用户名是否已存在
        if (this.lambdaQuery().eq(Account::getUsername, request.getUsername()).exists()) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否已存在
        if (this.lambdaQuery().eq(Account::getEmail, request.getEmail()).exists()) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 如果注册同时创建租户，校验租户编码
        if (Boolean.TRUE.equals(request.getCreateTenant())) {
            if (FuncUtils.isEmpty(request.getTenantName())) {
                throw new RuntimeException("租户名称不能为空");
            }
            if (FuncUtils.isEmpty(request.getTenantCode())) {
                throw new RuntimeException("租户编码不能为空");
            }
            if (tenantMapper.selectCount(
                    Wrappers.<Tenant>lambdaQuery().eq(Tenant::getCode, request.getTenantCode())) > 0) {
                throw new RuntimeException("租户编码已被使用");
            }
        }

        // 加入已有组织模式：校验目标租户
        if (request.getJoinTenantId() != null) {
            Tenant targetTenant = tenantMapper.selectById(request.getJoinTenantId());
            if (targetTenant == null) {
                throw new RuntimeException("目标组织不存在");
            }
            if (!Boolean.TRUE.equals(targetTenant.getJoinable())) {
                throw new RuntimeException("该组织不允许主动加入");
            }
        }

        // 创建账号
        Account account = new Account();
        account.setNickname(request.getNickname());
        account.setEmail(request.getEmail());
        account.setUsername(request.getUsername());
        account.setEnabled(true);

        // 保存以获取ID
        this.save(account);

        // 加密密码（使用用户ID作为盐值）
        String salt = account.getId().toString();
        String encryptedPassword = CryptoUtils.md5(request.getPassword(), salt);
        account.setPassword(encryptedPassword);

        // 更新密码
        this.updateById(account);

        // 同时创建租户
        if (Boolean.TRUE.equals(request.getCreateTenant())) {
            Tenant tenant = new Tenant();
            tenant.setName(request.getTenantName());
            tenant.setCode(request.getTenantCode());
            tenant.setDescription(request.getTenantDescription());
            tenant.setDiscoverable(false);
            tenant.setJoinable(false);
            tenant.setJoinApprovalRequired(true);
            tenantMapper.insert(tenant);

            // 将创建者设为租户拥有者
            AccountTenant membership = new AccountTenant();
            membership.setId(IdWorker.getId());
            membership.setAccountId(account.getId());
            membership.setTenantId(tenant.getId());
            membership.setRole(TenantRole.TENANT_OWNER);
            accountTenantService.save(membership);

            // 记录上次登录租户
            account.setLastTenantId(tenant.getId());
            this.updateById(account);

            // 初始化种子数据（params、内置 tools/hooks）
            tenantInitService.initTenantData(tenant.getId());
        }
        // 加入已有组织
        else if (request.getJoinTenantId() != null) {
            Tenant targetTenant = tenantMapper.selectById(request.getJoinTenantId());
            boolean needApproval = Boolean.TRUE.equals(targetTenant.getJoinApprovalRequired());

            if (!needApproval) {
                // 无需审批，直接加入
                AccountTenant membership = new AccountTenant();
                membership.setId(IdWorker.getId());
                membership.setAccountId(account.getId());
                membership.setTenantId(targetTenant.getId());
                membership.setRole(TenantRole.TENANT_VIEWER);
                accountTenantService.save(membership);

                // 记录上次登录租户
                account.setLastTenantId(targetTenant.getId());
                this.updateById(account);
            } else {
                // 需要审批，提交申请
                tenantJoinRequestService.submitRequest(
                        account.getId(), targetTenant.getId(), request.getJoinMessage());
            }
        } else {
            throw new RuntimeException("注册时必须创建租户或加入已有组织");
        }

        return true;
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        if (FuncUtils.isEmpty(request.getRefreshToken())) {
            throw new RuntimeException("refreshToken不能为空");
        }

        try {
            // 解析refreshToken
            Claims claims = TokenUtils.parseToken(request.getRefreshToken());
            String userId = claims.getId();

            // 查询用户
            Account account = this.getById(userId);
            if (account == null) {
                throw new RuntimeException("用户不存在");
            }

            // 检查用户是否被禁用
            if (Boolean.FALSE.equals(account.getEnabled())) {
                throw new RuntimeException("账号已被禁用，请联系管理员");
            }

            // 从原 token 中提取租户上下文
            Long tenantId = null;
            String tenantCode = null;
            TenantRole tenantRole = null;
            String tenantName = null;
            try {
                UserDetail oldDetail = JsonUtils.parse(claims.getSubject(), UserDetail.class);
                if (oldDetail != null && oldDetail.getTenantId() != null) {
                    tenantId = oldDetail.getTenantId();
                    tenantCode = oldDetail.getTenantCode();
                    tenantRole = oldDetail.getTenantRole() != null
                            ? TenantRole.valueOf(oldDetail.getTenantRole()) : null;
                }
            } catch (Exception ignored) {}

            // 生成新的token（保留租户上下文）
            return generateTokenResponse(account, tenantId, tenantCode, tenantRole, tenantName);
        } catch (Exception e) {
            throw new NotAuthException("refreshToken无效或已过期");
        }
    }

    @Override
    public void logout() {
        try {
            redisUtils.delete(RedisKeyBuilder.globalKey("login:" + TokenUtils.getToken()));
        } catch (Exception e) {
            throw new RuntimeException("退出登录失败");
        }
    }

    @Override
    public LoginResponse selectTenant(SelectTenantRequest request) {
        if (request == null || request.getTenantId() == null) {
            throw new RuntimeException("租户ID不能为空");
        }

        Long accountId = UserUtils.getId();

        // 验证账号在该租户中的成员身份
        AccountTenant membership = accountTenantService.getByAccountAndTenant(accountId, request.getTenantId());
        if (membership == null) {
            throw new RuntimeException("您不是该租户的成员");
        }

        // 查询账号和租户信息
        Account account = this.getById(accountId);
        if (account == null) {
            throw new RuntimeException("账号不存在");
        }

        Tenant tenant = tenantMapper.selectById(request.getTenantId());
        if (tenant == null) {
            throw new RuntimeException("租户不存在");
        }

        // 记录上次登录租户
        account.setLastTenantId(tenant.getId());
        this.updateById(account);

        // 生成带租户上下文的新 token
        LoginResponse response = generateTokenResponse(account, tenant.getId(), tenant.getCode(),
                membership.getRole(), tenant.getName());

        // 新 token 生成成功后再删除旧 token
        try {
            redisUtils.delete(RedisKeyBuilder.globalKey("login:" + TokenUtils.getToken()));
        } catch (Exception ignored) {}

        // 查询该账号所有租户成员关系，填充可选租户列表
        List<AccountTenant> allMemberships = accountTenantService.listByAccountId(accountId);
        List<UserDetail.TenantInfo> tenantInfoList = new ArrayList<>();
        for (AccountTenant at : allMemberships) {
            Tenant t = tenantMapper.selectById(at.getTenantId());
            tenantInfoList.add(new UserDetail.TenantInfo(
                    at.getTenantId(),
                    t != null ? t.getCode() : null,
                    t != null ? t.getName() : null,
                    at.getRole().name()
            ));
        }
        response.setTenants(tenantInfoList);

        return response;
    }

    @Override
    public boolean changePassword(ChangePasswordRequest request) {
        if (FuncUtils.isEmpty(request.getOldPassword()) || FuncUtils.isEmpty(request.getNewPassword())) {
            throw new RuntimeException("旧密码和新密码不能为空");
        }

        // 获取当前用户ID
        Long userId = UserUtils.getId();
        Account account = this.getById(userId);
        if (account == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码
        String salt = account.getId().toString();
        if (!passwordMatches(request.getOldPassword(), salt, account.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // 加密新密码
        String encryptedNewPassword = CryptoUtils.md5(request.getNewPassword(), salt);
        account.setPassword(encryptedNewPassword);

        return this.updateById(account);
    }

    @Override
    public boolean updateProfile(UpdateProfileRequest request) {
        // 获取当前用户ID
        Long userId = UserUtils.getId();
        Account account = this.getById(userId);
        if (account == null) {
            throw new RuntimeException("用户不存在");
        }

        // 更新昵称
        if (!FuncUtils.isEmpty(request.getNickname())) {
            account.setNickname(request.getNickname());
        }

        // 更新邮箱
        if (!FuncUtils.isEmpty(request.getEmail())) {
            // 检查邮箱是否已被其他用户使用
            if (this.lambdaQuery()
                    .eq(Account::getEmail, request.getEmail())
                    .ne(Account::getId, userId)
                    .exists()) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
            account.setEmail(request.getEmail());
        }

        account.setRememberLastTenant(request.getRememberLastTenant());

        return this.updateById(account);
    }

    @Override
    public boolean toggleEnabled(Long id, Boolean enabled) {
        Account account = this.getById(id);

        if (account == null) {
            throw new RuntimeException("用户不存在");
        }

        if (Objects.equals(account.getId(), SysConst.ADMIN_ACCOUNT_ID)) {
            throw new RuntimeException("管理员账号不可操作");
        }

        account.setEnabled(enabled);
        return this.updateById(account);
    }

    @Override
    public boolean adminChangePassword(Long id, String newPassword) {
        if (FuncUtils.isEmpty(newPassword)) {
            throw new RuntimeException("新密码不能为空");
        }

        Account account = this.getById(id);
        if (account == null) {
            throw new RuntimeException("用户不存在");
        }

        // 加密新密码
        String salt = account.getId().toString();
        String encryptedPassword = CryptoUtils.md5(newPassword, salt);
        account.setPassword(encryptedPassword);

        return this.updateById(account);
    }

    @Override
    public LoginResponse chatKeyToken(String chatKey, String userJwt) {
        if (FuncUtils.isEmpty(chatKey)) {
            return null;
        }

        // 使用JdbcTemplate绕过MyBatis-Plus租户自动过滤
        List<AgentChatKey> chatKeys = jdbcTemplate.query(
                "SELECT agent_code, chat_key, embed_secret, embed_secret_prev, tenant_id FROM agent_chat_key WHERE chat_key = ?",
                (rs, rowNum) -> {
                    AgentChatKey ack = new AgentChatKey();
                    ack.setAgentCode(rs.getString("agent_code"));
                    ack.setChatKey(rs.getString("chat_key"));
                    ack.setEmbedSecret(rs.getString("embed_secret"));
                    ack.setEmbedSecretPrev(rs.getString("embed_secret_prev"));
                    ack.setTenantId(rs.getLong("tenant_id"));
                    return ack;
                },
                chatKey
        );

        AgentChatKey agentChatKey = chatKeys.isEmpty() ? null : chatKeys.getFirst();
        if (agentChatKey == null) {
            return null;
        }

        // 嵌入身份验证（docs/identity-propagation-design.md §6.M6）：带了 userJwt 就必须验过，
        // 验不过拒绝——绝不静默降级为匿名（否则业务方以为带上了身份，实际权限判定拿到空）
        Claims externalClaims = FuncUtils.isEmpty(userJwt)
                ? null
                : verifyEmbedUserJwt(agentChatKey, userJwt);

        // 设置租户
        boolean hasNotCurrentTenant = TenantUtils.getCurrentTenantId() == null;
        if (hasNotCurrentTenant) {
            TenantUtils.setCurrentTenant(agentChatKey.getTenantId(), null);
        }

        try {
            String agentCode = agentChatKey.getAgentCode();
            AgentDefinition agent = agentDefinitionService.lambdaQuery()
                    .eq(AgentDefinition::getAgentCode, agentCode)
                    .one();
            if (agent == null || !Boolean.TRUE.equals(agent.getEnabled())) {
                return null;
            }

            Tenant tenant = tenantMapper.selectById(agent.getTenantId());
            if (tenant == null) {
                return null;
            }

            UserDetail.UserDetailBuilder userDetailBuilder = UserDetail.builder()
                    .id(IdWorker.getId())
                    .name(agent.getName())
                    .username(agent.getAgentCode())
                    .tenantId(tenant.getId())
                    .tenantCode(tenant.getCode())
                    // 渠道烙进 token：白名单判定与成本归因不再依赖易过期的 chatkey: 缓存
                    .authChannel(SysConst.CHANNEL_CHAT_KEY);
            if (externalClaims != null) {
                // 烙进会话 token（UserDetail 即 subject JSON），后续每次请求自动带出
                userDetailBuilder
                        .externalSub(externalClaims.getSubject())
                        .externalIss(chatKey)
                        .externalName(externalClaims.get("name", String.class));
            }
            UserDetail userDetail = userDetailBuilder.build();
            long neverExpireTtl = 100L * 365 * 24 * 60 * 60 * 1000;
            String token = TokenUtils.createToken(chatKey, userDetail, neverExpireTtl);

            // 存储到Redis（无过期时间）
            redisUtils.set(RedisKeyBuilder.globalKey("login:" + token), JsonUtils.toJsonStr(userDetail));

            return LoginResponse.builder()
                    .accessToken(token)
                    .accessTokenTTL(-1L)
                    .refreshToken(token)
                    .refreshTokenTTL(-1L)
                    .userDetail(userDetail)
                    .build();
        } finally {
            if (hasNotCurrentTenant) {
                TenantUtils.clear();
            }
        }
    }

    /**
     * 验证业务方签发的嵌入用户凭证（Intercom Identity Verification 模式）。
     *
     * <p>userJwt 由业务方后端用该 chatKey 的 embedSecret HMAC-SHA256 签发
     * （{sub: 业务方用户ID, name?: 显示名, exp: 建议 5 分钟}），只用于换 token 这一下。
     * 新旧双密钥依次验（轮换双活）；未启用 embedSecret 却带 userJwt 视为配置错误拒绝。
     *
     * @return 验签通过的 claims
     * @throws NotAuthException 未启用 / 签名无效 / 已过期
     */
    private Claims verifyEmbedUserJwt(AgentChatKey agentChatKey, String userJwt) {
        if (FuncUtils.isEmpty(agentChatKey.getEmbedSecret())
                && FuncUtils.isEmpty(agentChatKey.getEmbedSecretPrev())) {
            throw new NotAuthException("该 chatKey 未启用嵌入身份验证（embedSecret 未配置）");
        }

        Exception lastFailure = null;
        for (String secret : new String[]{agentChatKey.getEmbedSecret(), agentChatKey.getEmbedSecretPrev()}) {
            if (FuncUtils.isEmpty(secret)) {
                continue;
            }
            try {
                // HMAC key = secret 字符串的 UTF-8 字节（业务方 JWT 库的默认约定，
                // 接入文档同款示例）。不走 TokenUtils.generalKey——那是平台自有
                // JWT_SECRET_KEY 的"secret 视为 Base64 编码字节"约定，两边派生
                // 方式不一致会导致业务方按惯例签的 userJwt 永远验不过
                return io.jsonwebtoken.Jwts.parser()
                        .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .clockSkewSeconds(60)
                        .build()
                        .parseSignedClaims(userJwt)
                        .getPayload();
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // 过期与签名无效分开报：过期签名是对的，换把钥匙也救不回来
                throw new NotAuthException("嵌入用户凭证已过期，请业务方重新签发");
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        throw new NotAuthException("嵌入用户凭证签名无效"
                + (lastFailure != null ? "：" + lastFailure.getMessage() : ""));
    }

    /**
     * 生成Token响应
     *
     * @param account    账号信息
     * @param tenantId   租户ID（可选，null 表示无租户上下文）
     * @param tenantCode 租户编码
     * @param tenantRole 租户内角色
     * @param tenantName 租户名称
     * @return 登录响应
     */
    private LoginResponse generateTokenResponse(Account account, Long tenantId, String tenantCode,
                                                 TenantRole tenantRole, String tenantName) {
        String userId = account.getId().toString();

        // 查询该账号所有活跃的租户成员关系，填充可选租户列表
        List<UserDetail.TenantInfo> tenantInfoList = new ArrayList<>();
        try {
            List<AccountTenant> memberships = accountTenantService.listByAccountId(account.getId());
            for (AccountTenant at : memberships) {
                Tenant t = tenantMapper.selectById(at.getTenantId());
                if (t != null) {
                    tenantInfoList.add(new UserDetail.TenantInfo(
                            at.getTenantId(),
                            t.getCode(),
                            t.getName(),
                            at.getRole().name()
                    ));
                }
            }
        } catch (Exception ignored) {
            // 查询失败不影响登录流程
        }

        // 构建UserDetail
        UserDetail.UserDetailBuilder builder = UserDetail.builder()
                .id(account.getId())
                .username(account.getUsername())
                .name(account.getNickname())
                .email(account.getEmail())
                .tenantId(tenantId)
                .tenantCode(tenantCode)
                .tenantName(tenantName)
                .tenants(tenantInfoList);

        if (tenantRole != null) {
            builder.tenantRole(tenantRole.name());
        }

        UserDetail userDetail = builder.build();

        // 倘若当前没有租户上下文，设置完之后需要在 finally 中清除
        boolean hasNotCurrentTenant = TenantUtils.getCurrentTenantId() == null;
        try {
            // 设置当前租户
            TenantUtils.setCurrentTenant(tenantId, tenantCode);

            // 生成 accessToken 和 refreshToken
            long accessTokenTtl = Long.parseLong(paramsAdapter.getValue("ACCESS_TOKEN_TTL"));
            String accessToken = TokenUtils.createToken(userId, userDetail, accessTokenTtl);
            long refreshTokenTtl = Long.parseLong(paramsAdapter.getValue("REFRESH_TOKEN_TTL"));
            String refreshToken = TokenUtils.createToken(userId, userDetail, refreshTokenTtl);

            // 存储到Redis
            String userDetailStr = JsonUtils.toJsonStr(userDetail);
            redisUtils.setEx(RedisKeyBuilder.globalKey("login:" + accessToken), userDetailStr, accessTokenTtl, TimeUnit.MILLISECONDS);

            // 返回登录响应
            long currentTime = System.currentTimeMillis();
            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .accessTokenTTL(currentTime + accessTokenTtl)
                    .refreshToken(refreshToken)
                    .refreshTokenTTL(currentTime + refreshTokenTtl)
                    .userDetail(userDetail)
                    .build();
        } finally {
            if (hasNotCurrentTenant) {
                TenantUtils.clear();
            }
        }
    }

    /**
     * 密码匹配验证
     */
    private boolean passwordMatches(String requestPassword, String salt, String storedPassword) {
        if (FuncUtils.isEmpty(requestPassword) || FuncUtils.isEmpty(storedPassword)) {
            return false;
        }

        if (CryptoUtils.md5(requestPassword, salt).equals(storedPassword)) {
            return true;
        }

        String clientMd5Password = CryptoUtils.md5(requestPassword);
        if (CryptoUtils.md5(clientMd5Password, salt).equals(storedPassword)) {
            return true;
        }

        // Compatibility for older seed data that stored only the client-side MD5 value.
        return clientMd5Password.equals(storedPassword)
                || (isMd5Hex(requestPassword) && requestPassword.equals(storedPassword));
    }

    private boolean isMd5Hex(String value) {
        if (value == null || value.length() != 32) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验注册请求参数
     *
     * @param request 注册请求
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (FuncUtils.isEmpty(request.getUsername())) {
            throw new RuntimeException("用户名不能为空");
        }
        if (FuncUtils.isEmpty(request.getPassword())) {
            throw new RuntimeException("密码不能为空");
        }
        if (FuncUtils.isEmpty(request.getEmail())) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (FuncUtils.isEmpty(request.getNickname())) {
            throw new RuntimeException("昵称不能为空");
        }
    }

    /**
     * 查询用户所有 PENDING 状态的加入申请（绕过租户拦截器）
     * <p>
     * 登录时无租户上下文，MyBatis-Plus 租户拦截器会注入 tenant_id = null，
     * 导致查询永远为空，因此使用 jdbcTemplate 执行原生 SQL。
     *
     * @param accountId 账号ID
     * @return PENDING 状态的加入申请列表
     */
    private List<TenantJoinRequest> listAccountPendingRequests(Long accountId) {
        String sql = "SELECT id, tenant_id, account_id, status, message, reviewed_by, reviewed_at, created_at, updated_at FROM tenant_join_request WHERE account_id = ? AND status = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            TenantJoinRequest req = new TenantJoinRequest();
            req.setId(rs.getLong("id"));
            req.setTenantId(rs.getLong("tenant_id"));
            req.setAccountId(rs.getLong("account_id"));
            req.setStatus(TenantJoinRequestStatus.valueOf(rs.getString("status")));
            req.setMessage(rs.getString("message"));
            long reviewedBy = rs.getLong("reviewed_by");
            if (!rs.wasNull()) {
                req.setReviewedBy(reviewedBy);
            }
            if (rs.getTimestamp("reviewed_at") != null) {
                req.setReviewedAt(rs.getTimestamp("reviewed_at").toLocalDateTime());
            }
            if (rs.getTimestamp("created_at") != null) {
                req.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            if (rs.getTimestamp("updated_at") != null) {
                req.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }
            return req;
        }, accountId, TenantJoinRequestStatus.PENDING.name());
    }
}
