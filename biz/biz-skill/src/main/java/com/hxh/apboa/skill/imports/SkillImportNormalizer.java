package com.hxh.apboa.skill.imports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 导入前规范化技能目录（兼容常见命名差异）。
 */
public final class SkillImportNormalizer {

    private static final Logger log = LoggerFactory.getLogger(SkillImportNormalizer.class);

    /** Windows 路径非法字符及控制字符 */
    private static final Pattern ILLEGAL_PATH_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\u0000-\\u001f\\u007f]");
    /** 目录段尾部的空格/点（Windows 不允许段以空格或点结尾） */
    private static final Pattern TRAILING_DOT_SPACE = Pattern.compile("[. ]+(?=-|$)");
    /** 连续连字符 */
    private static final Pattern REPEATED_DASH = Pattern.compile("-{2,}");
    /** 首尾连字符 */
    private static final Pattern LEADING_TRAILING_DASH = Pattern.compile("^-+|-+$");
    /** 规范化后为空时的回退名 */
    private static final String FALLBACK_NAME = "unnamed-skill";

    private SkillImportNormalizer() {
    }

    /**
     * 规范化技能名为合法的文件系统目录名（Windows 兼容）。
     * <p>技能名来自 SKILL.md frontmatter，可能包含 /、\\、段尾空格等非法路径字符
     * （如 "Excel / XLSX"），直接拼接路径会抛出 InvalidPathException。
     * 规范化结果将同时用于文件系统目录与 DB 存储，保证两者一致。
     *
     * @param skillName 原始技能名
     * @return 规范化后的技能名
     */
    public static String normalizeSkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return FALLBACK_NAME;
        }
        String safe = skillName.trim()
                .replaceAll(ILLEGAL_PATH_CHARS.pattern(), "-")
                .replaceAll(TRAILING_DOT_SPACE.pattern(), "-")
                .replaceAll(REPEATED_DASH.pattern(), "-")
                .replaceAll(LEADING_TRAILING_DASH.pattern(), "");
        return safe.isEmpty() ? FALLBACK_NAME : safe;
    }

    /**
     * 将各技能子目录中的 skill.md 规范为 SKILL.md。
     */
    public static void normalizeSkillFiles(Path skillsDir) throws IOException {
        if (skillsDir == null || !Files.isDirectory(skillsDir)) {
            return;
        }
        try (Stream<Path> subdirs = Files.list(skillsDir)) {
            subdirs.filter(Files::isDirectory)
                    .filter(dir -> !SkillImportConstants.isNoiseDirectory(dir.getFileName().toString()))
                    .forEach(SkillImportNormalizer::normalizeSingleSkillDir);
        }
    }

    private static void normalizeSingleSkillDir(Path skillDir) {
        Path skillFile = skillDir.resolve(SkillImportConstants.SKILL_FILE);
        Path legacyFile = skillDir.resolve(SkillImportConstants.LEGACY_SKILL_FILE);
        try {
            if (Files.exists(skillFile) || !Files.exists(legacyFile)) {
                return;
            }
            Files.move(legacyFile, skillFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Renamed {} to SKILL.md in {}", SkillImportConstants.LEGACY_SKILL_FILE, skillDir.getFileName());
        } catch (IOException e) {
            log.warn("Failed to normalize skill file in {}: {}", skillDir, e.getMessage());
        }
    }
}
