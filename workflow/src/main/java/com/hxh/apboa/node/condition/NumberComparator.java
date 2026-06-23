package com.hxh.apboa.node.condition;

import java.math.BigDecimal;

/**
 * 数值比较工具类
 *
 * @author huxuehao
 */
public class NumberComparator {

    /**
     * 比较两个可能为数值类型（Long/Integer/Float/Double）的Object大小
     *
     * @param a 第一个数值对象（可为null）
     * @param b 第二个数值对象（可为null）
     * @return 负数（a < b）、0（a == b）、正数（a > b）
     * @throws IllegalArgumentException 若对象不是合法数值类型
     */
    public static int compare(Object a, Object b) {
        // 处理null情况（自定义规则：null小于任何非null值，两个null相等）
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        // 校验类型合法性
        if (!isValidNumberType(a) || !isValidNumberType(b)) {
            throw new IllegalArgumentException("对象必须是Long/Integer/Float/Double类型");
        }

        // 转换为BigDecimal进行高精度比较（避免double转换丢失精度）
        BigDecimal numA = toBigDecimal(a);
        BigDecimal numB = toBigDecimal(b);

        return numA.compareTo(numB);
    }

    /**
     * 检查对象是否为合法数值类型
     */
    private static boolean isValidNumberType(Object obj) {
        return obj instanceof Long
                || obj instanceof Integer
                || obj instanceof Float
                || obj instanceof Double;
    }

    /**
     * 将数值对象转换为BigDecimal（保留完整精度）
     */
    private static BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof Long) {
            return BigDecimal.valueOf((Long) obj);
        } else if (obj instanceof Integer) {
            return BigDecimal.valueOf((Integer) obj);
        } else if (obj instanceof Float) {
            return new BigDecimal(obj.toString());
        } else if (obj instanceof Double) {
            return new BigDecimal(obj.toString());
        }
        // 此处理论上不会走到，因为isValidNumberType已校验
        throw new IllegalArgumentException("不支持的数值类型: " + obj.getClass());
    }
}
