ALTER TABLE `skill_package`
    ADD COLUMN `alias` VARCHAR(500) DEFAULT NULL COMMENT '技能别名' AFTER `name`;
