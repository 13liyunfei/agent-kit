package com.codereview.kit.toolcalling;
import com.codereview.kit.model.ToolSchema;

import java.util.Map;

/**
 * 带护栏的工具装饰器：执行前按 schema 校验参数 + 危险模式拦截。
 *
 * <p>在注册处包装即可，工具本身无需感知：
 * <pre>
 * tools.register(new GuardedTool(new BuiltinTools.FileReadTool(baseDir)));
 * </pre>
 */
public class GuardedTool implements AgentTool {

    private final AgentTool delegate;
    private final ToolSchema schema;

    public GuardedTool(AgentTool delegate) {
        this.delegate = delegate;
        this.schema = ToolSchema.from(delegate);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public String parameterSchema() {
        return delegate.parameterSchema();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        ToolSchemaValidator.Validation v = ToolSchemaValidator.validate(schema, args);
        if (!v.valid()) {
            return ToolResult.fail("参数校验未通过: " + v.reason());
        }
        return delegate.execute(args);
    }
}
