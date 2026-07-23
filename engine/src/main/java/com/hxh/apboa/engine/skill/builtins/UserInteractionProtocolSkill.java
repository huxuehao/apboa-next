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
    public AgentSkill getAgentSkill() {
        return AgentSkill.builder()
                .name(SKILL_NAME)
                .description(
                        "Used to build user interaction interfaces and collect user input. " +
                        "When necessary information is missing during task execution, or users need to fill out forms, select processing methods, confirm operations or supplement business parameters, use this protocol to generate APIP interactive messages. " +
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
            Used for quick selection scenarios.
            
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
