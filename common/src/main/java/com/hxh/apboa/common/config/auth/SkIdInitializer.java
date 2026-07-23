package com.hxh.apboa.common.config.auth;

import com.hxh.apboa.common.consts.TableConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 描述：启动时回填 API Key 内存集合（所有依赖 common 的服务通用）
 *
 * <p>SK 鉴权依赖 {@link AuthInterceptor} 的进程内静态集合，运行期靠 Redis 广播
 * 增量同步，但广播不可重放——服务重启后必须从 DB 全量回填，否则已存在的 SK
 * 在该服务上全部报"SK已失效"（历史 bug：回填组件曾只存在于 biz-sk 模块，
 * 仅 console 生效，runtime 等服务重启即失效）。
 *
 * <p>必须使用 JdbcTemplate 裸 SQL：回填是全租户的，MyBatis-Plus 查询会被
 * 租户插件过滤成单租户。
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class SkIdInitializer implements ApplicationRunner {
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.warn("当前服务无数据源，SK 鉴权集合未回填：@SkAccess 接口将拒绝所有已存在的 SK，仅能靠 Redis 广播同步新建的 SK");
            return;
        }
        try {
            List<Long> skIds = jdbcTemplate.query(
                    "SELECT id FROM " + TableConst.SECRET_KEY + " WHERE enabled = 1",
                    (rs, rowNum) -> rs.getLong("id"));
            skIds.forEach(AuthInterceptor::addSkId);
            log.info("API Key 初始化完成，已加载 {} 个", skIds.size());
        } catch (Exception e) {
            log.error("API Key 初始化失败", e);
            throw new RuntimeException(e);
        }
    }
}
