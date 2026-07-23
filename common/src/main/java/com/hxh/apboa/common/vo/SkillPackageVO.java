package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.enums.SkillType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能包VO
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode
public class SkillPackageVO implements SerializableEnable {
    private Long id;
    private String name;
    private String alias;
    private String description;
    private String category;
    private SkillType skillType;
    private String classPath;
    private ScopeType scopeType;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private List<Object> used;
    private List<Long> tools;
}
