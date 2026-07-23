package com.hxh.apboa.console.account;

import com.hxh.apboa.account.service.AccountService;
import com.hxh.apboa.account.service.AccountTenantService;
import com.hxh.apboa.account.service.TenantJoinRequestService;
import com.hxh.apboa.account.service.TenantService;
import com.hxh.apboa.common.config.auth.PassAuth;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.dto.TenantCreateRequest;
import com.hxh.apboa.common.dto.TenantJoinRequestDTO;
import com.hxh.apboa.common.dto.TenantMemberAddDTO;
import com.hxh.apboa.common.dto.TenantSettingsDTO;
import com.hxh.apboa.common.entity.Account;
import com.hxh.apboa.common.entity.AccountTenant;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.entity.TenantJoinRequest;
import com.hxh.apboa.common.enums.TenantJoinRequestStatus;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.TenantDiscoveryVO;
import com.hxh.apboa.common.vo.TenantJoinRequestVO;
import com.hxh.apboa.common.vo.TenantMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户管理Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final AccountTenantService accountTenantService;
    private final TenantJoinRequestService tenantJoinRequestService;
    private final AccountService accountService;

    // ==================== 基础 CRUD ====================

    /**
     * 租户列表
     */
    @GetMapping("/list")
    public R<List<Tenant>> list() {
        return R.data(tenantService.list());
    }

    /**
     * 租户详情
     */
    @GetMapping("/{id}")
    public R<Tenant> detail(@PathVariable("id") Long id) {
        Tenant entity = tenantService.getById(id);
        if (entity == null) {
            return R.fail("租户不存在");
        }
        return R.data(entity);
    }

    /**
     * 创建租户（任何登录用户均可创建，创建者自动成为租户拥有者）
     */
    @PostMapping
    public R<Tenant> create(@RequestBody TenantCreateRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setCode(request.getCode());
        tenant.setDescription(request.getDescription());
        tenant.setContactName(request.getContactName());
        tenant.setContactEmail(request.getContactEmail());

        Tenant created = tenantService.createTenant(tenant, UserUtils.getId());
        return R.data(created, "租户创建成功");
    }

    /**
     * 更新租户基本信息（系统管理员）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody Tenant tenant) {
        tenant.setId(id);
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, tenant.getId())) {
            throw new BusinessException("无权限修改");
        }
        return R.data(tenantService.updateById(tenant));
    }

    /**
     * 删除租户（系统管理员）
     */
    @RoleNeed({TenantRole.TENANT_OWNER})
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, id)) {
            throw new BusinessException("无权限删除");
        }
        return R.data(tenantService.removeById(id));
    }

    // ==================== 租户治理 ====================

    /**
     * 发现可加入的租户列表（含已加入的租户及成员身份信息）
     */
    @GetMapping("/discoverable")
    public R<List<TenantDiscoveryVO>> listDiscoverable() {
        return R.data(tenantService.listDiscoverable(UserUtils.getId()));
    }

    /**
     * 发现可加入的租户列表
     */
    @PassAuth
    @GetMapping("/pass-auth/discoverable")
    public R<List<TenantDiscoveryVO>> listPassAuthDiscoverable() {
        return R.data(tenantService.listPassAuthDiscoverable());
    }

    /**
     * 更新租户治理设置（租户管理员 或 系统管理员）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/{id}/settings")
    public R<Tenant> updateSettings(@PathVariable("id") Long tenantId,
                                     @RequestBody TenantSettingsDTO settings) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw new BusinessException("无权限修改");
        }
        return R.data(tenantService.updateSettings(tenantId, settings), "设置已更新");
    }

    // ==================== 成员管理 ====================

    /**
     * 租户成员列表（含账户信息）
     */
    @GetMapping("/{id}/members")
    public R<List<TenantMemberVO>> members(@PathVariable("id") Long id) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, id)) {
            throw new BusinessException("无权限查看");
        }

        List<AccountTenant> memberships = accountTenantService.listByTenantId(id);
        if (memberships.isEmpty()) {
            return R.data(Collections.emptyList());
        }

        // 批量查询账户信息
        Set<Long> accountIds = memberships.stream()
                .map(AccountTenant::getAccountId)
                .collect(Collectors.toSet());
        Map<Long, Account> accountMap = accountService.listByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a, (a, b) -> a));

        // 构建VO列表
        List<TenantMemberVO> result = new ArrayList<>();
        for (AccountTenant m : memberships) {
            Account account = accountMap.get(m.getAccountId());
            TenantMemberVO vo = new TenantMemberVO();
            vo.setAccountId(m.getAccountId());
            vo.setNickname(account != null ? account.getNickname() : null);
            vo.setEmail(account != null ? account.getEmail() : null);
            vo.setUsername(account != null ? account.getUsername() : null);
            vo.setTenantRole(m.getRole().name());
            vo.setJoinedAt(m.getCreatedAt());
            vo.setEnabled(m.getEnabled());
            result.add(vo);
        }
        return R.data(result);
    }

    /**
     * 直接添加成员（通过用户名查找账户）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PostMapping("/{id}/members")
    public R<Boolean> addMember(@PathVariable("id") Long tenantId,
                                 @RequestBody TenantMemberAddDTO dto) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw new BusinessException("无权限添加成员");
        }

        // 根据用户名查找账户
        Account account = accountService.lambdaQuery()
                .eq(Account::getUsername, dto.getUsername())
                .one();
        if (account == null) {
            return R.fail("账号不存在：" + dto.getUsername());
        }
        tenantService.addMember(tenantId, account.getId(), dto.getRole().name());
        return R.data(true, "成员添加成功");
    }

    /**
     * 修改成员角色（租户管理员 或 租户拥有者）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/{id}/members/{accountId}/role")
    public R<Boolean> updateMemberRole(@PathVariable("id") Long tenantId,
                                       @PathVariable("accountId") Long accountId,
                                       @RequestParam("role") TenantRole role) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw new BusinessException("无权限修改成员角色");
        }

        TenantRole targetRole = getTargetTenantRole(tenantId, accountId);
        if (targetRole == null) {
            return R.fail("该成员不属于此租户");
        }
        // 拥有者角色不允许被任何人修改
        if (targetRole == TenantRole.TENANT_OWNER) {
            throw new BusinessException("拥有者角色不允许修改");
        }
        // 不允许将成员角色设置为拥有者
        if (role == TenantRole.TENANT_OWNER) {
            throw new BusinessException("不允许将成员角色设置为拥有者");
        }
        TenantRole callerRole = getCallerTenantRole(tenantId);
        if (callerRole == TenantRole.TENANT_ADMIN) {
            // 管理员不能修改管理员
            if (targetRole == TenantRole.TENANT_ADMIN) {
                throw new BusinessException("无权修改管理员的角色");
            }
            // 管理员不能将成员设置为管理员
            if (role == TenantRole.TENANT_ADMIN) {
                throw new BusinessException("无权将成员角色设置为管理员");
            }
        }
        return R.data(accountTenantService.updateRole(accountId, tenantId, role));
    }

    /**
     * 移除成员（租户管理员 或 租户拥有者）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @DeleteMapping("/{id}/members/{accountId}")
    public R<Boolean> removeMember(@PathVariable("id") Long tenantId,
                                   @PathVariable("accountId") Long accountId) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw new BusinessException("无权限移除成员");
        }

        AccountTenant membership = accountTenantService.getByAccountAndTenant(accountId, tenantId);
        if (membership == null) {
            return R.fail("该成员不属于此租户");
        }
        // 拥有者不允许被移除
        if (membership.getRole() == TenantRole.TENANT_OWNER) {
            throw new BusinessException("拥有者不允许被移除");
        }
        // 管理员只能由拥有者移除
        TenantRole callerRole = getCallerTenantRole(tenantId);
        if (callerRole == TenantRole.TENANT_ADMIN && membership.getRole() == TenantRole.TENANT_ADMIN) {
            throw new BusinessException("管理员只能由拥有者移除");
        }
        return R.data(accountTenantService.removeById(membership.getId()));
    }

    /**
     * 获取当前用户在指定租户中的精确角色
     */
    private TenantRole getCallerTenantRole(Long tenantId) {
        AccountTenant membership = accountTenantService.getByAccountAndTenant(
                UserUtils.getId(), tenantId);
        if (membership == null) {
            throw new BusinessException("当前用户不属于此租户");
        }
        return membership.getRole();
    }

    /**
     * 获取目标成员在租户中的角色
     */
    private TenantRole getTargetTenantRole(Long tenantId, Long accountId) {
        AccountTenant membership = accountTenantService.getByAccountAndTenant(accountId, tenantId);
        if (membership == null) {
            return null;
        }
        return membership.getRole();
    }

    // ==================== 加入申请管理 ====================

    /**
     * 申请加入租户
     */
    @PostMapping("/{id}/join-request")
    public R<TenantJoinRequest> joinRequest(@PathVariable("id") Long tenantId,
                                             @RequestBody TenantJoinRequestDTO dto) {
        Tenant tenant = tenantService.getById(tenantId);
        if (tenant == null) {
            return R.fail("租户不存在");
        }
        if (!Boolean.TRUE.equals(tenant.getJoinable())) {
            return R.fail("该租户不允许主动申请加入");
        }

        // 如果无需审批，直接加入
        if (!Boolean.TRUE.equals(tenant.getJoinApprovalRequired())) {
            tenantService.addMember(tenantId, UserUtils.getId(), TenantRole.TENANT_VIEWER.name());
            return R.success("加入成功");
        }

        TenantJoinRequest request = tenantJoinRequestService.submitRequest(
                UserUtils.getId(), tenantId, dto.getMessage());
        return R.data(request, "申请已提交，请等待审批");
    }

    /**
     * 查看租户的加入申请列表（含申请人信息）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @GetMapping("/{id}/join-requests")
    public R<List<TenantJoinRequestVO>> listJoinRequests(@PathVariable("id") Long tenantId) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw new BusinessException("无权限查看");
        }

        List<TenantJoinRequest> requests = tenantJoinRequestService.listByTenantId(tenantId);
        if (requests.isEmpty()) {
            return R.data(Collections.emptyList());
        }

        // 批量查询申请人账户信息
        Set<Long> accountIds = requests.stream()
                .map(TenantJoinRequest::getAccountId)
                .collect(Collectors.toSet());
        Map<Long, Account> accountMap = accountService.listByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a, (a, b) -> a));

        // 构建VO列表
        List<TenantJoinRequestVO> result = new ArrayList<>();
        for (TenantJoinRequest r : requests) {
            Account account = accountMap.get(r.getAccountId());
            TenantJoinRequestVO vo = new TenantJoinRequestVO();
            vo.setId(r.getId());
            vo.setTenantId(r.getTenantId());
            vo.setAccountId(r.getAccountId());
            vo.setApplicantName(account != null ? account.getNickname() : null);
            vo.setApplicantUsername(account != null ? account.getUsername() : null);
            vo.setStatus(r.getStatus());
            vo.setMessage(r.getMessage());
            vo.setReviewedBy(r.getReviewedBy());
            vo.setReviewedAt(r.getReviewedAt());
            vo.setCreatedAt(r.getCreatedAt());
            vo.setUpdatedAt(r.getUpdatedAt());
            result.add(vo);
        }
        return R.data(result);
    }

    /**
     * 审批通过加入申请（租户管理员）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/join-request/{requestId}/approve")
    public R<Boolean> approveJoinRequest(@PathVariable("requestId") Long requestId) {
        TenantJoinRequest request = tenantJoinRequestService.getById(requestId);
        if (request == null) {
            return R.fail("申请不存在");
        }
        // 校验申请属于当前用户管理的租户，防止越权操作其他组织的申请
        if (!request.getTenantId().equals(UserUtils.getTenantId())) {
            return R.fail("无权操作其他组织的申请");
        }
        tenantJoinRequestService.approve(requestId, UserUtils.getId());
        return R.data(true, "已通过申请");
    }

    /**
     * 审批拒绝加入申请（租户管理员）
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @PutMapping("/join-request/{requestId}/reject")
    public R<Boolean> rejectJoinRequest(@PathVariable("requestId") Long requestId) {
        TenantJoinRequest request = tenantJoinRequestService.getById(requestId);
        if (request == null) {
            return R.fail("申请不存在");
        }
        // 校验申请属于当前用户管理的租户，防止越权操作其他组织的申请
        if (!request.getTenantId().equals(UserUtils.getTenantId())) {
            return R.fail("无权操作其他组织的申请");
        }
        tenantJoinRequestService.reject(requestId, UserUtils.getId());
        return R.data(true, "已拒绝申请");
    }

    /**
     * 撤销自己的加入申请
     */
    @DeleteMapping("/join-request/{requestId}/cancel")
    public R<Boolean> cancelJoinRequest(@PathVariable("requestId") Long requestId) {
        TenantJoinRequest request = tenantJoinRequestService.getById(requestId);
        if (request == null) {
            return R.fail("申请不存在");
        }
        if (!request.getAccountId().equals(UserUtils.getId())) {
            return R.fail("无权操作他人的申请");
        }
        if (request.getStatus() != TenantJoinRequestStatus.PENDING) {
            return R.fail("当前状态不允许撤销");
        }
        tenantJoinRequestService.cancelRequest(requestId);
        return R.data(true, "已撤销申请");
    }

    /**
     * 查看自己的所有加入申请记录
     */
    @GetMapping("/my-join-requests")
    public R<List<TenantJoinRequest>> myJoinRequests() {
        Long accountId = UserUtils.getId();
        return R.data(tenantJoinRequestService.listByAccountId(accountId));
    }
}
