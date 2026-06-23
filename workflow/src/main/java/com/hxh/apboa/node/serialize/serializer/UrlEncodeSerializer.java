package com.hxh.apboa.node.serialize.serializer;

import com.hxh.apboa.node.serialize.Config;
import com.hxh.apboa.node.serialize.Serializer;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * 描述：url_encode序列化器
 *
 * @author huxuehao
 **/
public class UrlEncodeSerializer implements Serializer {
    @Override
    public String serialize(Object data, Config config) throws Exception {
        if (data == null) {
            return null;
        }
        if (isPrimitiveOrWrapper(data.getClass())) {
            return URLEncoder.encode(data.toString(), config.getEncoding());
        }

        // 支持对对象类型的URL_ENCODE序列化
        ArrayList<String> keyValuePairs  = new ArrayList<>();
        Field[] fields = data.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            Object fieldValue = field.get(data);

            String encodedValue = (fieldValue != null)
                    ? URLEncoder.encode(fieldValue.toString(), config.getEncoding())
                    : "";
            keyValuePairs.add(fieldName + "=" + encodedValue);
        }

        return String.join("&", keyValuePairs);

    }

    // 判断是否为基本类型或包装类（如int、Integer、String等）
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == String.class
                || clazz == Boolean.class
                || clazz == Float.class
                || clazz == Double.class;
    }
}
