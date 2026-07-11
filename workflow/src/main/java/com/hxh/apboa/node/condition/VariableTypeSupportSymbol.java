package com.hxh.apboa.node.condition;

import com.hxh.apboa.node.base.inputout.OutputConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：变量类型支持的运算符
 *
 * @author huxuehao
 **/
public class VariableTypeSupportSymbol {
    private static final Map<ScopeAndType, List<Config.Symbol>> verifyMap = new HashMap<>() {{
        put(new ScopeAndType(OutputConfig.VariableType.String, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.CONTAINS);
            add(Config.Symbol.NOT_CONTAINS);
            add(Config.Symbol.STARTS_WITH);
            add(Config.Symbol.ENDS_WITH);
            add(Config.Symbol.EQUALS);
            add(Config.Symbol.NOT_EQUALS);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.String, Config.Scope.LENGTH), new ArrayList<>() {{
            add(Config.Symbol.EQ);
            add(Config.Symbol.NE);
            add(Config.Symbol.GT);
            add(Config.Symbol.LT);
            add(Config.Symbol.GE);
            add(Config.Symbol.LE);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Integer, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.EQ);
            add(Config.Symbol.NE);
            add(Config.Symbol.GT);
            add(Config.Symbol.LT);
            add(Config.Symbol.GE);
            add(Config.Symbol.LE);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Long, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.EQ);
            add(Config.Symbol.NE);
            add(Config.Symbol.GT);
            add(Config.Symbol.LT);
            add(Config.Symbol.GE);
            add(Config.Symbol.LE);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Double, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.EQ);
            add(Config.Symbol.NE);
            add(Config.Symbol.GT);
            add(Config.Symbol.LT);
            add(Config.Symbol.GE);
            add(Config.Symbol.LE);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Float, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.EQ);
            add(Config.Symbol.NE);
            add(Config.Symbol.GT);
            add(Config.Symbol.LT);
            add(Config.Symbol.GE);
            add(Config.Symbol.LE);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Array, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.CONTAINS);
            add(Config.Symbol.NOT_CONTAINS);
            add(Config.Symbol.IS_ALL);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Array, Config.Scope.LENGTH), new ArrayList<>() {{
            add(Config.Symbol.EQ);
            add(Config.Symbol.NE);
            add(Config.Symbol.GT);
            add(Config.Symbol.LT);
            add(Config.Symbol.GE);
            add(Config.Symbol.LE);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Object, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.EXPRESSION);
        }});

        put(new ScopeAndType(OutputConfig.VariableType.Boolean, Config.Scope.SELF), new ArrayList<>() {{
            add(Config.Symbol.IS_TRUE);
            add(Config.Symbol.IS_FALSE);
        }});
    }};

    //验证是否支持运算符
    public static void verifySupportSymbol(OutputConfig.VariableType  inputType ,
                                           Config.Scope scope,
                                           Config.Symbol symbol) {
        List<Config.Symbol> symbols = verifyMap.get(new ScopeAndType(inputType, scope));
        if (symbols == null || !symbols.contains(symbol)) {
            throw new RuntimeException(inputType +":" + scope + " 不支持 "+ symbol +" 运算符");
        }
    }
}
