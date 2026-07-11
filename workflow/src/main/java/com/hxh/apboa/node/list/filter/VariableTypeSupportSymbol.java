package com.hxh.apboa.node.list.filter;

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
    private static final Map<SupportType, List<SimpleSymbol>> verifyMap = new HashMap<>() {{
        put(SupportType.STRING, new ArrayList<>() {{
            add(SimpleSymbol.CONTAINS);
            add(SimpleSymbol.NOT_CONTAINS);
            add(SimpleSymbol.STARTS_WITH);
            add(SimpleSymbol.ENDS_WITH);
            add(SimpleSymbol.EQUALS);
            add(SimpleSymbol.NOT_EQUALS);
        }});

        put(SupportType.NUMBER, new ArrayList<>() {{
            add(SimpleSymbol.EQ);
            add(SimpleSymbol.NE);
            add(SimpleSymbol.GT);
            add(SimpleSymbol.LT);
            add(SimpleSymbol.GE);
            add(SimpleSymbol.LE);
        }});

        put(SupportType.BOOLEAN, new ArrayList<>() {{
            add(SimpleSymbol.IS_FALSE);
            add(SimpleSymbol.IS_TRUE);
        }});

    }};

    //验证是否支持运算符
    public static void verifySupportSymbol(SupportType inputType , SimpleSymbol symbol) {
        List<SimpleSymbol> symbols = verifyMap.get(inputType);
        if (symbols == null || !symbols.contains(symbol)) {
            throw new RuntimeException(inputType +" 不支持 "+ symbol +" 运算符");
        }
    }
}
