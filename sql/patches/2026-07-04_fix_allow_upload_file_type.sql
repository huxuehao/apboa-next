-- 修复 params 表里图片/音频/视频上传白名单的脏值。
--
-- 背景：ALLOW_IMAGE_FILE_TYPE 原值 'png,jpeg,png,gif,webp' 里 png 重复一份、
-- jpg 整个缺失（导致附件按钮选不中 .jpg 文件）；音频/视频两个参数相比前端
-- ChatInputToolbar.vue 的 IMAGE_EXTS/AUDIO_EXTS/VIDEO_EXTS 设计期分类清单
-- 也大幅缺格式。本次以前端集合为准补齐三者。
-- 关联改动：SysConst.java 常量同步修正、sql/once_db_init/db_init.sql 新环境
-- 初始化值同步修正——本脚本只对已经跑起来、数据库里已有这三条记录的环境生效。

UPDATE `params` SET `param_value` = 'png,jpg,jpeg,gif,webp,bmp,svg,ico'
WHERE `param_key` = 'ALLOW_IMAGE_FILE_TYPE';

UPDATE `params` SET `param_value` = 'mp3,wav,ogg,m4a,flac,aac,wma,mpeg'
WHERE `param_key` = 'ALLOW_AUDIO_FILE_TYPE';

UPDATE `params` SET `param_value` = 'mp4,webm,mov,mkv,avi,flv,m3u8,mpeg'
WHERE `param_key` = 'ALLOW_VIDEO_FILE_TYPE';
