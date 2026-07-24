package com.hxh.apboa.engine.skill.builtins;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.engine.skill.IBuiltinSkill;
import io.agentscope.core.skill.AgentSkill;
import org.springframework.stereotype.Component;

/**
 * 描述：用户交互协议SKILL（内置，全局）
 *
 * @author huxuehao
 **/
@Component
@Scope(ScopeType.GLOBAL)
public class UserInteractionProtocolSkill implements IBuiltinSkill {
    private static final String SKILL_NAME = "user_interaction_protocol_rules";

    @Override
    public String getSysPrompt() {
        return """
                ===================================================
                Core Principle
                > If you **cannot provide a reliable, responsible and practically valuable answer without additional information**, you must first try to obtain that information yourself using the tools and skills available to you. Only when the missing information is inherently user-specific and cannot be obtained by any available tool should you invoke `user_interaction_protocol_rules` to ask the user, before delivering any substantive response.

                Before responding to a question, ask yourself:
                > "Do I have sufficient information to give a reliable answer?"

                - If **yes** → Answer directly.
                - If **no, but the missing information can be obtained by an available tool or skill** (e.g. locating, searching the web, reading files, calling an API) → use that tool or skill first to gather the information, then answer.
                - If **no, and the missing information is inherently user-specific and cannot be obtained by any available tool** → invoke `user_interaction_protocol_rules` to ask the user for more information.
                """;
    }

    @Override
    public AgentSkill getAgentSkill() {
        return AgentSkill.builder()
                .name(SKILL_NAME)
                .description(
                        "Used to build user interaction interfaces and collect user input. " +
                        "When necessary information is missing during task execution, or users need to fill out forms, select processing methods, confirm operations or supplement business parameters, use this protocol to generate APIP interactive messages. " +
                        "Also use it AFTER completing an analysis or forming conclusions: present the candidate conclusions, directions or next-step options as a choice interaction and let the user decide before proceeding. " +
                        "It supports three types of interactive components: form, choice and confirm. This skill shall only be applied when user participation in decision-making or data input is required. " +
                        "Do NOT use this protocol for scenarios that require no user interaction, such as general Q&A, knowledge explanation, content creation, code generation, solution analysis and text processing. Simply reply with natural language instead."
                )
                .skillContent(buildSkillContent())
                .build();
    }

    private static String buildSkillContent() {
        return """
            # Core Principles
            ## 1. Flat Structure First
            - Maximum nesting depth ≤ 2
            - No complex recursive structures
            - Field semantics must be intuitive
            - Maintain stable and predictable structure
            
            ## 2. Form Restrictions
            - A single form is recommended to contain no more than 6 fields
            - Split long workflows into multiple independent forms
            - Implement multi-step business processes via multi-turn conversations
            - No step transition capabilities provided at the protocol layer
            
            ## 3. Linkage Restrictions
            Only simple equality judgment is allowed:
            ```json
            {
              "dependsOn": {
                "field": "field_name",
                "value": "target_value"
              }
            }
            ```
            
            Complex logic is prohibited:
            ```text
            &&
            ||
            !
            >
            <
            >=
            <=
            ```
            
            Only the following condition is supported:
            ```text
            field == value
            ```
            
            ## 4. Natural Language Priority
            All interactive messages must include the following field:
            ```json
            {
              "content": "Natural language guidance for users"
            }
            ```
            
            Pure interactive components without text content are forbidden.
            
            Valid Example:
            ```json
            {
              "content": "Please fill in your contact information",
              "interaction": {}
            }
            ```
            
            Invalid Example:
            ```json
            {
              "interaction": {}
            }
            ```
            
            ## 5. Backward Protocol Compatibility
            When clients encounter unknown interaction types:
            - No errors thrown
            - Rendering will not be interrupted
            - Ignore the unrecognized interactive component
            - Continue to display the `content` text
            
            # AI Output Structure
            ```json
            {
              "role": "assistant",
              "content": "Natural language description",
              "interaction": {}
            }
            ```
            
            # Unified Structure for Interaction
            All interactive components follow a unified structure:
            ```json
            {
              "id": "unique_id",
              "type": "form | choice | confirm",
              "meta": {},
              "props": {}
            }
            ```
            
            ## Field Description
            | Field | Type | Required | Description |
            |-------|------|----------|-------------|
            | id | string | Yes | Unique identifier of the component |
            | type | string | Yes | Type of interactive component |
            | meta | object | No | Extended business data |
            | props | object | No | Component properties |
            
            ## Supported Interaction Types
            | Type | Usage |
            |------|-------|
            | form | Information entry |
            | choice | Quick selection |
            | confirm | Final confirmation |
            
            # General Properties (props)
            ```json
            {
              "title": "",
              "submitLabel": "Submit",
              "cancelLabel": "Cancel",
              "disabled": false,
              "readonly": false
            }
            ```
            
            ## Field Description
            | Field | Type | Default Value | Description |
            |-------|------|---------------|-------------|
            | title | string | Empty | Component title |
            | submitLabel | string | Submit | Text of the submit button |
            | cancelLabel | string | Cancel | Text of the cancel button |
            | disabled | boolean | false | Disable the entire component |
            | readonly | boolean | false | Set the entire component to read-only |
            
            # Form
            Used for information entry scenarios.
            
            ## Basic Structure
            ```json
            {
              "id": "user_form",
              "type": "form",
              "props": {
                "title": "User Information"
              },
              "fields": []
            }
            ```
            
            # Form Field Structure
            ```json
            {
              "name": "username",
              "label": "Full Name",
              "type": "text",
              "required": true
            }
            ```
            
            ## Field Definition
            | Field | Type | Required | Description |
            |-------|------|----------|-------------|
            | name | string | Yes | Unique identifier of the field |
            | label | string | Yes | Display name of the field |
            | type | string | Yes | Control type |
            | required | boolean | No | Whether the field is mandatory |
            | placeholder | string | No | Placeholder prompt text |
            | defaultValue | any | No | Default value |
            | options | array | No | Selection options |
            | multiple | boolean | No | Whether to support multiple selection |
            | dependsOn | object | No | Linkage rules |
            | validations | array | No | Validation rules |
            | helpText | string | No | Auxiliary description |
            | disabled | boolean | No | Disable the field |
            | readonly | boolean | No | Set the field to read-only |
            | hidden | boolean | No | Hide the field |
            | errorText | string | No | Error prompt text |
            
            ## Supported Field Types
            ### Text
            ```text
            text
            textarea
            number
            ```
            
            ### Time
            ```text
            date
            datetime
            ```
            
            ### Single Selection
            ```text
            select
            radio
            ```
            
            ### Multiple Selection
            ```text
            checkbox
            checkbox-group
            ```
            
            ### Status Switch
            ```text
            switch
            ```
            
            ### Special Format
            ```text
            email
            tel
            ```
            
            ## Option Structure
            ```json
            [
              {
                "value": "beijing",
                "label": "Beijing",
                "disabled": false
              }
            ]
            ```
            
            ## Linkage Rules
            ```json
            {
              "dependsOn": {
                "field": "trip_type",
                "value": "round"
              }
            }
            ```
            
            Rule:
            ```text
            field == value
            ```
            
            When the condition is not met:
            - The field will be hidden
            - Excluded from validation
            - Excluded from data submission
            
            ## Validation Rules
            Supported rule types:
            ```text
            required
            pattern
            min
            max
            email
            tel
            ```
            
            Example:
            ```json
            [
              {
                "type": "required",
                "message": "This field is required"
              },
              {
                "type": "min",
                "value": 1,
                "message": "Value cannot be less than 1"
              }
            ]
            ```
            
            # Choice
            Used for quick selection and decision-making (ASK) scenarios.

            ## When to Use (ASK Pattern)
            Proactively ask the user to decide in the following situations:
            - After completing an analysis: present the conclusions or candidate directions as options and let the user pick one before proceeding
            - At key decision points where multiple viable approaches exist
            - When a conclusion needs user approval before acting on it

            ASK rules:
            - Provide 2-6 options; each option SHOULD include a one-sentence `description` explaining its meaning or consequence
            - Set `allowCustom: true` by default, so the user can always express an idea beyond the presets
            - Use `multiple: false` when options are mutually exclusive directions; use `multiple: true` only when selections can be combined
            - After the user responds, continue reasoning based on the selection (or the custom input)

            ## Basic Structure
            ```json
            {
              "id": "order_choice",
              "type": "choice",
              "question": "Please select the processing method",
              "multiple": false,
              "allowCustom": false,
              "options": []
            }
            ```
            
            ## Field Definition
            | Field | Type | Required | Description |
            |-------|------|----------|-------------|
            | question | string | Yes | Prompt question |
            | options | array | Yes | Selection options |
            | multiple | boolean | No | Enable multiple selection |
            | allowCustom | boolean | No | Allow custom input |
            
            ## Option Structure
            ```json
            [
              {
                "value": "refund",
                "label": "Apply for Refund",
                "description": "Funds will be returned via the original path",
                "disabled": false
              }
            ]
            ```
            
            ## Rules
            - `value`: Data submitted by the component
            - `label`: Text displayed on the page
            - Frontend must use `value` as the unique identifier

            ## Complete Example (ASK after analysis)
            ```uip
            {
              "content": "The sales data analysis is complete. I identified three viable directions. Which one should we proceed with?",
              "interaction": {
                "id": "analysis_decision",
                "type": "choice",
                "question": "Please choose the next direction",
                "multiple": false,
                "allowCustom": true,
                "options": [
                  { "value": "expand_a", "label": "Expand Product Line A", "description": "Highest growth (+32%), but requires additional inventory investment" },
                  { "value": "optimize_b", "label": "Optimize pricing of Product Line B", "description": "Stable demand; price elasticity suggests ~8% revenue upside" },
                  { "value": "phase_out_c", "label": "Phase out Product Line C", "description": "Declining for 6 consecutive months; frees up resources" }
                ]
              }
            }
            ```

            # Confirm
            Used for final confirmation scenarios.
            
            ## Basic Structure
            ```json
            {
              "id": "confirm_order",
              "type": "confirm",
              "message": "Confirm to submit the order?",
              "confirmLabel": "Confirm",
              "cancelLabel": "Cancel",
              "payload": {}
            }
            ```
            
            ## Field Definition
            | Field | Type | Required | Description |
            |-------|------|----------|-------------|
            | message | string | Yes | Confirmation prompt content |
            | confirmLabel | string | No | Text of the confirm button |
            | cancelLabel | string | No | Text of the cancel button |
            | payload | object | No | Business-related data |
            
            # Code Generation Specifications
            All content must comply with the following rules:
            1. The `content` field must be generated
            2. It is recommended to keep no more than 1 item in `interaction`
            3. A form is recommended to contain no more than 6 fields
            4. Undefined component types are prohibited
            5. Undefined field types are prohibited
            6. Undefined protocol attributes are prohibited
            7. Prioritize natural language when uncertain
            8. Do not generate empty forms
            9. Do not generate empty options
            10. Do not generate empty `interaction` arrays
            11. For decision-making scenarios (picking a direction, approving a conclusion, choosing among approaches), prefer `choice` over `form`, and set `allowCustom: true`
            
            Valid Example:
            ```json
            {
              "content": "Please select the processing method",
              "interaction": {
                "id": "order_choice",
                "type": "choice"
              }
            }
            ```
            
            Invalid Example:
            ```json
            {
              "content": "Please select the processing method",
              "interaction": {}
            }
            ```
            
            # UIP Message Encapsulation Specifications
            When returning UIP interactive content, wrap the content with an `uip` code block.
            
            Standard Format:
            ```uip
            {
              "role": "assistant",
              "content": "Natural language description",
              "interaction": {}
            }
            ```
            
            ## Rules
            1. Use the `uip` code block to wrap UIP content
            2. The content inside must be valid JSON format
            3. The `content` field is mandatory
            5. The `interaction` field is mandatory
            6. Do not use `json` or other types of code blocks
            7. Do not output multiple `uip` code blocks
            8. Do not add explanatory text inside the `uip` code block
            
            ## Explanation
            - The client only parses content within the `uip` code block
            - Content outside the `uip` code block is treated as plain text
            - The `uip` code block is not required for ordinary chat replies
            - Only use the `uip` code block when forms, selections, confirmations or other interactive components are needed
            """;
    }
}
