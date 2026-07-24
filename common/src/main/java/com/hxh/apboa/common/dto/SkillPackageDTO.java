package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能包查询DTO
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPackageDTO extends PageParams {

    /**
     * 搜索关键词：按 name 或 alias 模糊匹配（在 SkillPackageController.page 手动组 OR 条件）。
     * 不加 @QueryDefine：通用 ConditionBuilder 只能拼 AND，而 name/alias 需要 OR。
     */
    private String name;

    @QueryDefine(value = "技能分类", condition = QueryCondition.EQ)
    private String category;

    @QueryDefine(value = "技能类型", condition = QueryCondition.EQ)
    private String skillType;

    @QueryDefine(value = "是否可用", condition = QueryCondition.EQ)
    private Boolean enabled;
}
