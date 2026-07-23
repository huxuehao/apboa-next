/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.spring.boot.agui.mvc;

import io.agentscope.core.agui.processor.AguiRequestProcessor.ResumeDecision;
import java.util.List;

/**
 * HITL resume 请求体（docs/hitl-confirmation-refactor.md §6.3）。
 *
 * @param decisions 逐工具确认决策（toolUseId/name/approved）；null 或空表示全部允许
 * @param memoryActive 是否开启长期记忆（决定 resume 完成后保留还是删除 session）
 */
public record ResumeRequest(List<ResumeDecision> decisions, boolean memoryActive) {}
