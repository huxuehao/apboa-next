package com.hxh.apboa.common.consts;

import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.TenantUtils;

import java.util.List;

/**
 * 描述：系统常量
 *
 * @author huxuehao
 **/
public class SysConst {
    /**
     * JWT密钥
     */
    public static final String JWT_SECRET_KEY = "jwt.secret";
    /**
     * 登录用户Key
     */
    public static final String LOGIN_USER_KEY = "LOGIN-USER-KEY";
    public static final String USER_DETAIL = "USER-DETAIL";

    /**
     * token过期时间（6小时）
     */
    public static final Long ACCESS_TOKEN_TTL = 1000 * 60 * 60 * 6L;

    /**
     * refresh token过期时间（12小时）
     */
    public static final Long REFRESH_TOKEN_TTL = 1000 * 60 * 60 * 18L;

    /**
     * 单个文件的最大体积（单位：MB）
     */
    public static final String SINGLE_FILE_MAX_SIZE = "5";

    /**
     * 语音输入单次最长时长（单位：秒）
     */
    public static final String ASR_MAX_DURATION = "60";

    /**
     * 语音识别输出繁体转简体（Qwen3-ASR 自动语种检测偶发繁体方向，模型与接口层无简繁参数）
     */
    public static final String ASR_TRADITIONAL_TO_SIMPLE = "true";

    /**
     * 语音帧能量门限（dBFS，高于此值的帧视为语音；正常说话约 -30~-10，安静底噪约 -60~-45）
     */
    public static final String ASR_SILENCE_DB = "-40";

    /**
     * 最小语音帧占比（百分比整数，低于此比例判定为静音录音、拒绝识别——生成式 ASR 对静音会输出幻觉文本）
     */
    public static final String ASR_MIN_VOICE_RATIO = "3";

    public static final String ALLOW_IMAGE_FILE_TYPE = "png,jpeg,png,gif,webp";
    public static final String ALLOW_AUDIO_FILE_TYPE = "mp3,wav,mpeg";
    public static final String ALLOW_VIDEO_FILE_TYPE = "mp4,mpeg";
    public static final String ALLOW_DOC_FILE_TYPE = "doc,docx,pdf,txt,md";
    public static final String ALLOW_EXCEL_FILE_TYPE = "xlsx,xls,csv";
    public static final String ALLOW_PPT_FILE_TYPE = "pptx,ppt";

    public static final Long ADMIN_ACCOUNT_ID = 1111111111111111111L;

    public static final String CURRENT_NODE_ID = CryptoUtils.uuid();

    // 工作空间相关
    public static final String ROOT_DIR_NAME = ".apboa";
    public static final String TENANTS_DIR_NAME = "tenants";
    public static final String WORKSPACE_DIR_NAME = "workspaces";
    public static final String SKILLS_DIR_NAME = "skills";
    public static final double WORKSPACE_CAPACITY_MB = 30; // 工作空间容量限制（单位：MB）

    /**
     * 获取多租户隔离的 workspace 路径
     * 格式: .apboa/tenants/{tenantCode}/workspaces
     */
    public static String getWorkspacePath() {
        String tenantCode = TenantUtils.getCurrentTenantCode();
        return getWorkspacePath(tenantCode);
    }

    /**
     * 获取多租户隔离的 workspace 路径
     * 格式: .apboa/tenants/{tenantCode}/workspaces
     *
     * @param tenantCode 租户编号
     */
    public static String getWorkspacePath(String tenantCode) {
        if (tenantCode == null) {
            throw new RuntimeException("租户编号不可为空");
        }
        return ROOT_DIR_NAME + "/" + TENANTS_DIR_NAME + "/" + tenantCode + "/" + WORKSPACE_DIR_NAME;
    }

    /**
     * 获取多租户隔离的 skills 目录路径
     * 格式: .apboa/tenants/{tenantCode}/skills
     */
    public static String getSkillsDir() {
        String tenantCode = TenantUtils.getCurrentTenantCode();
        return getSkillsDir(tenantCode);
    }

    /**
     * 获取多租户隔离的 skills 目录路径
     * 格式: .apboa/tenants/{tenantCode}/skills
     *
     * @param tenantCode 租户编号
     */
    public static String getSkillsDir(String tenantCode) {
        if (tenantCode == null) {
            throw new RuntimeException("租户编号不可为空");
        }
        return ROOT_DIR_NAME + "/" + TENANTS_DIR_NAME + "/" + tenantCode + "/" + SKILLS_DIR_NAME;
    }

    /**
     * 获取租户根路径
     * 格式: .apboa/tenants/{tenantCode}
     */
    public static String getTenantPath(String tenantCode) {
        return ROOT_DIR_NAME + "/" + TENANTS_DIR_NAME + "/" + tenantCode;
    }

    // 工作空间钩子错误键
    public static final String WORKSPACE_HOOK_ERROR_KEY = "workspace_hook_error";

    // Skill Source
    public static final String SKILL_SOURCE = "apboa";
}
