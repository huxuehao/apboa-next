package com.hxh.apboa.workflowbiz.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class WorkflowResourceRefs {
    private Set<String> cacheIds = new LinkedHashSet<>();
    private Set<String> datasourceIds = new LinkedHashSet<>();
    private Set<String> mqIds = new LinkedHashSet<>();
    private Set<String> pluginIds = new LinkedHashSet<>();
}
