package com.hxh.apboa.engine.skill;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.SkillFileType;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.skill.builtins.UserInteractionProtocolSkill;
import com.hxh.apboa.engine.skill.builtins.VisionEnhancementProtocolSkill;
import com.hxh.apboa.engine.tool.ToolkitFactory;
import com.hxh.apboa.engine.workspace.skills.SearchReplaceSkill;
import com.hxh.apboa.engine.workspace.skills.WorkspaceSkill;
import com.hxh.apboa.skill.service.AgentSkillPackageService;
import com.hxh.apboa.skill.service.SkillFileService;
import com.hxh.apboa.skill.service.SkillPackageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.skill.service.SkillToolService;
import com.hxh.apboa.tool.service.ToolService;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.coding.ShellCommandTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 描述：skill 构造器
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class SkillBoxFactory {
    private final SkillPackageService skillPackageService;
    private final SkillFileService skillFileService;
    private final AgentSkillPackageService agentSkillPackageService;
    private final SkillToolService skillToolService;
    private final ToolService toolService;

    /**
     * 获取SkillBox
     *
     * @param codeExecutionConfig   代码执行配置
     * @param checkedSkillPackages  agent 勾选且启用的技能包（{@link #getCheckedSkillPackages}）
     * @return SkillBox
     */
    public SkillBox getSkillBox(CodeExecutionConfig codeExecutionConfig, List<SkillPackage> checkedSkillPackages) {
        return getSkillBox(new Toolkit(), codeExecutionConfig, checkedSkillPackages);
    }

    /**
     * 根据技能包ID列表构建SkillBox。
     *
     * @param skillPackageIds 技能包ID列表
     * @param toolkit         工具箱
     * @return SkillBox
     */
    public SkillBox getSkillBox(List<Long> skillPackageIds, Toolkit toolkit) {
        Toolkit currentToolkit = toolkit == null ? new Toolkit() : toolkit;
        SkillBox skillBox = new SkillBox(currentToolkit);

        // 注册智能体基础技能，不启用代码执行能力。
        skillBox.registerSkill(UserInteractionProtocolSkill.getAgentSkill());
        skillBox.registerSkill(VisionEnhancementProtocolSkill.getAgentSkill());

        if (skillPackageIds == null || skillPackageIds.isEmpty()) {
            return skillBox;
        }

        registerSkills(skillBox, skillPackageIds, currentToolkit);
        return skillBox;
    }

    /**
     * 获取SkillBox
     *
     * @param toolkit               工具包
     * @param codeExecutionConfig   代码执行配置
     * @param checkedSkillPackages  agent 勾选且启用的技能包（{@link #getCheckedSkillPackages}）
     * @return SkillBox
     */
    public SkillBox getSkillBox(Toolkit toolkit, CodeExecutionConfig codeExecutionConfig, List<SkillPackage> checkedSkillPackages) {
        SkillBox skillBox = new SkillBox(toolkit);

        configureCodeExecution(skillBox, codeExecutionConfig);

        // 注册技能包（清单已按勾选+enabled 过滤，与系统提示词注入共用同一份）
        checkedSkillPackages.forEach(skillPackage -> registerSkill(skillBox, skillPackage, toolkit));

        return skillBox;
    }

    /**
     * 查 agent 勾选且启用的技能包。一次 agent 构建周期内系统提示词注入
     * （AgentSysPromptFactory 遍历内置技能的 getSysPrompt）与 SkillBox 注册共用：
     * 调用方查一次传两处，保证两边勾选口径一致且不重复查库。
     */
    public List<SkillPackage> getCheckedSkillPackages(Long agentDefinitionId) {
        List<Long> skillPackageIds = agentSkillPackageService.getSkillPackageIds(agentDefinitionId);
        if (skillPackageIds.isEmpty()) {
            return List.of();
        }
        return skillPackageService.listByIds(skillPackageIds).stream()
                .filter(SkillPackage::getEnabled)
                .toList();
    }

    /**
     * 注册单个技能包
     *
     * @param skillBox SkillBox
     * @param skillPackage 技能包
     * @param toolkit    工具包
     */
    private void registerSkill(SkillBox skillBox, SkillPackage skillPackage, Toolkit toolkit) {
        // 查询技能包的所有入库文件
        List<SkillFile> files = skillFileService.listBySkillId(skillPackage.getId());
        // 注册技能包中的工具
        List<Long> toolIds = skillToolService.getToolIds(skillPackage.getId());
        List<ToolConfig> toolConfigs = null;
        if (!toolIds.isEmpty()) {
            toolConfigs = toolService.listByIds(toolIds);
            ToolkitFactory.registerTools(toolkit, toolConfigs);
        }

        // 查找 SKILL.md 文件
        String skillContent = files.stream()
                .filter(f -> f.getFileType() == SkillFileType.SKILL_MD)
                .map(SkillFile::getContent)
                .findFirst()
                .orElse("");

        List<SkillFile> resourceFiles = files.stream()
                .filter(f -> f.getFileType() != SkillFileType.SKILL_MD)
                .toList();

        AgentSkill baseSkill = AgentSkill.builder()
                .name(skillPackage.getName())
                .description(skillPackage.getDescription())
                .skillContent((skillContent))
                .build();

        AgentSkill.Builder skillBuilder = baseSkill.toBuilder()
                .skillContent(
                        addToolInfoToContent(
                                appendResourceUsageHint(baseSkill, resourceFiles),
                                toolConfigs))
                .source(SysConst.SKILL_SOURCE);

        // 添加所有资源引用（references/examples/scripts 类型的文件）
        files.stream()
                .filter(f -> f.getFileType() != SkillFileType.SKILL_MD)
                .forEach(f -> skillBuilder.addResource(f.getFilePath(), f.getContent()));

        skillBox.registration().skill(skillBuilder.build()).apply();
    }

    /**
     * 添加资源使用提示
     *
     * @param baseSkill     基础技能
     * @param resourceFiles 资源文件列表
     * @return 添加资源使用提示后的技能内容
     */
    private String appendResourceUsageHint(AgentSkill baseSkill, List<SkillFile> resourceFiles) {
        if (resourceFiles == null || resourceFiles.isEmpty()) {
            return baseSkill.getSkillContent();
        }

        List<String> resourcePaths = resourceFiles.stream()
                .filter(f -> f.getFileType() != SkillFileType.SKILL_MD)
                .map(SkillFile::getFilePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> path.replace('\\', '/'))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (resourcePaths.isEmpty()) {
            return baseSkill.getSkillContent();
        }

        String skillContent = baseSkill.getSkillContent();
        String resourceList = resourcePaths.stream()
                .map(path -> "- `" + path + "`")
                .collect(Collectors.joining("\n"));

        String hint = """
            
            ================ Skill Resources Explanation ==================
            
            When this skill refers to files in this directory, examples/, references/, or scripts/,
            treat them as skill resources, not workspace files. Load them with:
            
            `load_skill_through_path(skillId="%s", path="<resource-path>")`
            
            Available resource paths:
            %s""".formatted(baseSkill.getSkillId(), resourceList);

        return (skillContent == null ? "" : skillContent) + hint;
    }

    /**
     * 添加工具信息到技能内容
     *
     * @param content       技能内容
     * @param toolConfigs   工具配置列表
     * @return 添加工具信息后的技能内容
     */
    private String addToolInfoToContent(String content, List<ToolConfig> toolConfigs) {
        if (toolConfigs == null || toolConfigs.isEmpty()) {
            return content;
        }

        String toolInfo = toolConfigs.stream()
                .map(tool -> "toolName：" + tool.getToolId() + "\ntoolDesc: " + tool.getDescription())
                .collect(Collectors.joining("\n---\n"));

        return content + "\n\n================ Available Tools (You can use the following tools) ==============\n" + toolInfo;
    }

    /**
     * 配置代码执行环境
     *
     * @param skillBox SkillBox
     * @param config   代码执行配置
     */
    private void configureCodeExecution(SkillBox skillBox, CodeExecutionConfig config) {
        if (skillBox == null || config == null) {
            return;
        }

        // 配置工作空间专属skill
        skillBox.registerSkill(WorkspaceSkill.getAgentSkill());

        // 设置自动上传
        skillBox.setAutoUploadSkill(false);

        // 配置代码执行环境
        SkillBox.CodeExecutionBuilder codeExecutionBuilder = skillBox.codeExecution();

        // 设置工作目录
        codeExecutionBuilder.workDir(SysConst.getWorkspacePath() + "/" + AgentContext.get().getThreadId());

        // 配置Shell命令工具
        if (Boolean.TRUE.equals(config.getEnableShell())) {
            Set<String> allowedCommands = parseAllowedCommands(config.getCommand());
            codeExecutionBuilder.withShell(new ShellCommandTool(null, allowedCommands, null));
        }

        // 配置文件读写工具
        if (Boolean.TRUE.equals(config.getEnableRead())) {
            codeExecutionBuilder.withRead();
        }
        if (Boolean.TRUE.equals(config.getEnableWrite())) {
            codeExecutionBuilder.withWrite();
            // 配置工作空间专属skill
            skillBox.registerSkill(SearchReplaceSkill.getAgentSkill());
        }

        codeExecutionBuilder.enable();
    }

    /**
     * 解析允许执行的命令集合
     *
     * @param commandJson 命令JSON节点
     * @return 允许执行的命令集合
     */
    private Set<String> parseAllowedCommands(JsonNode commandJson) {
        Set<String> commands = new HashSet<>();
        if (commandJson == null || commandJson.isEmpty()) {
            return commands;
        }
        if (commandJson.isArray()) {
            commandJson.forEach(node -> commands.add(node.asText()));
        }
        return commands;
    }
}
