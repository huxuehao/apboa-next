package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode
public class SkillsHubVO implements SerializableEnable {
    private String category;
    private String slug;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String version;
    private String name;
    private String downloads;
    private String homepage;
    private String iconUrl;
    private String requiresApiKey;

}
