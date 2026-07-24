package com.hxh.apboa.engine.skill.builtins;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.engine.skill.IBuiltinSkill;
import io.agentscope.core.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 描述：视觉增强协议SKILL（内置，全局）
 *
 * @author huxuehao
 **/
@Component
@Scope(ScopeType.GLOBAL)
public class VisionEnhancementProtocolSkill implements IBuiltinSkill {
    private static final String SKILL_NAME = "vision_enhancement_protocol_rules";

    @Override
    public String getSysPrompt() {
        return """
                ===================================================
                Core Principle
                > **Only use** the `vision_enhancement_protocol_rules` feature for visual presentation **when** it can **significantly improve comprehension** compared to plain text.

                Before responding, ask yourself:
                > "Would presenting part of the content as cards or charts help the user understand the information faster and better?"

                - If **yes** → Respond using the `vision_enhancement_protocol_rules`
                - If **no** → Reply with plain text only
                """;
    }

    @Override
    public AgentSkill getAgentSkill() {
        return AgentSkill.builder()
                .name(SKILL_NAME)
                .description(
                        "When natural language struggles to convey information efficiently, VEP can produce information Cards and Charts to help users quickly understand data, status, trends, and analytical results.\n" +
                                "\n" +
                                "Applicable scenarios (not limited to):\n" +
                                "\n" +
                                "* Data analysis\n" +
                                "* Statistical result presentation\n" +
                                "* Metric summarization\n" +
                                "* Trend analysis\n" +
                                "* Proportion analysis\n" +
                                "* Business overview\n" +
                                "* User information display\n" +
                                "* Order information display\n" +
                                "* System status display\n" +
                                "\n" +
                                "Not applicable for:\n" +
                                "\n" +
                                "* User input\n" +
                                "* Form collection\n" +
                                "* User selection\n" +
                                "* User confirmation\n" +
                                "\n" +
                                "The goal of VEP is:\n" +
                                "\n" +
                                "> To achieve the most stable AI generation capability and the best user visual experience with minimal protocol complexity."
                )
                .skillContent(buildSkillContent())
                .build();
    }

    private static String buildSkillContent() {
        return """
            # Core Principles
            
            ## 1. Natural Language First
            
            All VEP messages MUST include:
            ```json
            {
              "content": "natural language description"
            }
            ```
            Visual components are solely for enhancing presentation.
            Returning visual components alone is prohibited.
            
            Correct:
            ```vep
            {
              "role": "assistant",
              "content": "Below is the sales data analysis result for this month.",
              "vision": {}
            }
            ```
            
            Incorrect:
            ```vep
            {
              "vision": {}
            }
            ```
            
            
            ## 2. Visual Enhancement, Not Visual Replacement
            
            Users should be able to understand the core content even without viewing visual components.
            
            Therefore:
            * content MUST fully convey the core conclusions
            * card is used for summary display
            * chart is used for trend and statistical display
            
            ## 3. Minimal Component Model
            
            VEP supports only two component types:
            ```text
            card
            chart
            ```
            
            Generating other component types is prohibited.
            
            Examples of forbidden types:
            ```text
            metric
            kpi
            dashboard
            widget
            panel
            summary
            report
            ```
            None of these are permitted.
            
            # Output Structure
            
            ```json
            {
              "role": "assistant",
              "content": "natural language description",
              "vision": {}
            }
            ```
            
            # Vision Unified Structure
            
            ```json
            {
              "id": "unique_id",
              "type": "card | chart",
              "title": "Title",
              "insight": "Insight summary",
              "data": {}
            }
            ```
            
            ## Field Definitions
            
            | Field    | Type   | Required | Description                |
            | -------- | ------ | -------- | -------------------------- |
            | id       | string | Yes      | Component unique identifier |
            | type     | string | Yes      | card or chart              |
            | title    | string | No       | Component title            |
            | insight  | string | No       | AI-generated insight       |
            | data     | object | Yes      | Component data             |
            
            # Card
            
            Used for displaying structured information.
            
            Applicable for:
            * User information
            * Order information
            * Project details
            * KPI metrics
            * System status
            * Result summary
            
            ## Card Structure
            
            ```json
            {
              "id": "summary_card",
              "type": "card",
              "title": "Core Metrics",
              "insight": "Sales revenue increased 15% month-over-month",
              "data": []
            }
            ```
            
            ## Card Item
            
            ```json
            {
              "label": "Sales Revenue",
              "value": 45800,
              "format": "currency",
              "unit": "¥"
            }
            ```
            
            ## Item Fields
            
            | Field  | Type   | Required | Description    |
            | ------ | ------ | -------- | -------------- |
            | label  | string | Yes      | Display name   |
            | value  | any    | Yes      | Data value     |
            | format | string | No       | Display format |
            | unit   | string | No       | Unit           |
            | status | string | No       | Status value   |
            
            
            ## Supported Formats
            
            ```text
            text
            number
            currency
            percent
            datetime
            badge
            ```
            
            ## Badge States
            
            ```text
            success
            warning
            error
            info
            default
            ```
            
            ## Example
            
            ```json
            {
              "id": "order_card",
              "type": "card",
              "title": "Order Information",
              "insight": "Order has been paid, awaiting shipment",
              "data": [
                {
                  "label": "Order No.",
                  "value": "ORD-001"
                },
                {
                  "label": "Amount",
                  "value": 5999,
                  "format": "currency",
                  "unit": "¥"
                },
                {
                  "label": "Status",
                  "value": "Paid",
                  "format": "badge",
                  "status": "success"
                }
              ]
            }
            ```
            
            # Chart
            
            Used for displaying statistical charts.
            The client uniformly uses ECharts for rendering.
            AI does NOT generate ECharts options.
            AI only generates standard data structures.
            
            ## Chart Structure
            ```json
            {
              "id": "sales_chart",
              "type": "chart",
              "title": "Sales Trend",
              "insight": "Sales revenue has been growing steadily over the past 7 days",
              "data": {}
            }
            ```
            
            ## Chart Data
            ```json
            {
              "chartType": "line",
              "xAxis": [],
              "series": []
            }
            ```
            
            ## Field Definitions
            | Field      | Type   | Required | Description   |
            | ---------- | ------ | -------- | ------------- |
            | chartType  | string | Yes      | Chart type    |
            | xAxis      | array  | No       | X-axis data   |
            | series     | array  | Yes      | Data series   |
            | yAxisLabel | string | No       | Y-axis label  |
            
            ## Supported Chart Types
            ```text
            line
            bar
            pie
            area
            radar
            ```
            
            # Series
            
            ## Standard Charts
            ```json
            {
              "name": "Sales Revenue",
              "data": [100,200,300]
            }
            ```
            
            ## Pie Chart
            ```json
            {
              "name": "Model Proportion",
              "data": [
                {
                  "name": "GPT",
                  "value": 50
                },
                {
                  "name": "DeepSeek",
                  "value": 30
                }
              ]
            }
            ```
            
            # Chart Examples
            
            ## Line Chart
            ```json
            {
              "id": "sales_chart",
              "type": "chart",
              "title": "7-Day Sales Trend",
              "insight": "Overall sustained growth trend",
              "data": {
                "chartType": "line",
                "xAxis": [
                  "Mon",
                  "Tue",
                  "Wed",
                  "Thu",
                  "Fri"
                ],
                "series": [
                  {
                    "name": "Sales Revenue",
                    "data": [
                      100,
                      180,
                      260,
                      320,
                      450
                    ]
                  }
                ]
              }
            }
            ```
            
            
            ## Bar Chart
            ```json
            {
              "id": "compare_chart",
              "type": "chart",
              "title": "Model Call Count",
              "insight": "GPT has the highest usage count",
              "data": {
                "chartType": "bar",
                "xAxis": [
                  "GPT",
                  "Claude",
                  "DeepSeek"
                ],
                "series": [
                  {
                    "name": "Call Count",
                    "data": [
                      520,
                      380,
                      210
                    ]
                  }
                ]
              }
            }
            ```
            
            
            ## Pie Chart
            ```json
            {
              "id": "distribution_chart",
              "type": "chart",
              "title": "Model Usage Proportion",
              "insight": "GPT accounts for 50% of total calls",
              "data": {
                "chartType": "pie",
                "series": [
                  {
                    "name": "Proportion",
                    "data": [
                      {
                        "name": "GPT",
                        "value": 50
                      },
                      {
                        "name": "Claude",
                        "value": 30
                      },
                      {
                        "name": "DeepSeek",
                        "value": 20
                      }
                    ]
                  }
                ]
              }
            }
            ```
            
            # Code Block Wrapping Specification
            
            When AI returns vision-enhanced content, the following format MUST be used:
            ```vep
            {
              "role": "assistant",
              "content": "natural language description",
              "vision": {}
            }
            ```
            
            CRITICAL: `vision` MUST be a single object `{}`, NEVER an array `[]`.
            Each `vep` code block contains exactly ONE visual component.
            To output multiple visual components, use multiple `vep` code blocks.
            
            # Generation Rules
            
            AI MUST follow:
            1. MUST include content
            2. MUST include vision as a single object `{}`, NEVER as an array `[]`
            3. MUST use `vep` code block
            4. Each `vep` block contains exactly ONE visual component
            5. Only card and chart are allowed
            6. HTML generation is NOT allowed
            7. SVG generation is NOT allowed
            8. ECharts option generation is NOT allowed
            9. JavaScript generation is NOT allowed
            10. Empty data components are NOT allowed
            11. card is recommended to have ≤ 12 fields
            12. chart is recommended to have ≤ 50 data points
            13. A single message is recommended to have ≤ 3 visual components (via multiple `vep` blocks)
            14. When uncertain, prefer natural language
            
            # Component Selection Guide
            
            | Scenario                       | Recommended Component |
            | ------------------------------ | --------------------- |
            | User details                   | card                  |
            | Order details                  | card                  |
            | System status                  | card                  |
            | KPI metrics                    | card                  |
            | Data summary                   | card                  |
            | Trend analysis                 | chart(line)           |
            | Data comparison                | chart(bar)            |
            | Proportion analysis            | chart(pie)            |
            | Multi-dimensional capability analysis | chart(radar)      |
            
            # Example
            
            ```vep
            {
              "role": "assistant",
              "content": "This month's sales performance is strong, with total revenue of ¥45,800, up 15% month-over-month.",
              "vision": {
                "id": "sales_summary",
                "type": "card",
                "title": "Core Metrics",
                "insight": "Sales revenue increased 15% month-over-month",
                "data": [
                  {
                    "label": "Sales Revenue",
                    "value": 45800,
                    "format": "currency",
                    "unit": "¥"
                  },
                  {
                    "label": "Order Count",
                    "value": 156,
                    "format": "number"
                  }
                ]
              }
            }
            ```
            
            ```vep
            {
              "role": "assistant",
              "content": "This month's sales performance is strong, with total revenue of ¥45,800, up 15% month-over-month.",
              "vision": {
                "id": "sales_trend",
                "type": "chart",
                "title": "Sales Trend",
                "insight": "Continuous growth over the last 7 days",
                "data": {
                  "chartType": "line",
                  "xAxis": [
                    "Mon",
                    "Tue",
                    "Wed"
                  ],
                  "series": [
                    {
                      "name": "Sales Revenue",
                      "data": [
                        100,
                        200,
                        300
                      ]
                    }
                  ]
                }
              }
            }
            ```
            """;
    }
}
