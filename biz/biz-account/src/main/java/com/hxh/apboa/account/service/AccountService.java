package com.hxh.apboa.account.service;

import com.hxh.apboa.common.dto.*;
import com.hxh.apboa.common.entity.Account;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 账号Service
 *
 * @author huxuehao
 */
public interface AccountService extends IService<Account> {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 是否成功
     */
    boolean register(RegisterRequest request);

    /**
     * 刷新Token
     *
     * @param request 刷新请求
     * @return 登录响应
     */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * 用户退出
     */
    void logout();

    /**
     * 修改密码
     *
     * @param request 修改密码请求
     * @return 是否成功
     */
    boolean changePassword(ChangePasswordRequest request);

    /**
     * 修改个人信息
     *
     * @param request 修改个人信息请求
     * @return 是否成功
     */
    boolean updateProfile(UpdateProfileRequest request);

    /**
     * 禁用/激活用户
     *
     * @param id 用户ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean toggleEnabled(Long id, Boolean enabled);

    /**
     * 管理员修改用户密码
     *
     * @param id 用户ID
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean adminChangePassword(Long id, String newPassword);

    /**
     * 通过ChatKey换取Token
     *
     * @param chatKey 对话Key
     * @param userJwt 业务方后端用 embedSecret 签的嵌入用户凭证（可空=纯匿名；
     *                非空必须验签通过，否则拒绝——docs/identity-propagation-design.md §6.M6）
     * @return token，如果验证失败返回null
     */
    LoginResponse chatKeyToken(String chatKey, String userJwt);

    /**
     * 选择租户（多租户登录后选择进入的租户）
     *
     * @param request 租户选择请求
     * @return 登录响应（含租户上下文的 token）
     */
    LoginResponse selectTenant(SelectTenantRequest request);
}
