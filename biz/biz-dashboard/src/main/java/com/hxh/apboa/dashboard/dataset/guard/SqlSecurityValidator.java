package com.hxh.apboa.dashboard.dataset.guard;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 描述：SQL 安全校验器。解析一次 SQL，抽取引用对象，按顺序执行所有安全规则。
 *
 * @author huxuehao
 **/
@Slf4j
@Service
public class SqlSecurityValidator {
    private final List<SqlGuard> guards;

    public SqlSecurityValidator(List<SqlGuard> guards) {
        this.guards = guards.stream()
                .sorted(Comparator.comparingInt(Ordered::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 校验并返回清洗后的 SQL（去除结尾分号）。校验不通过时抛出 DatasetSecurityException。
     */
    public String validate(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new DatasetSecurityException("查询语句不能为空");
        }
        String cleaned = stripTrailingSemicolon(rawSql.trim());
        if (cleaned.contains(";")) {
            throw new DatasetSecurityException("数据集不允许多条语句");
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(cleaned);
        } catch (Exception e) {
            throw new DatasetSecurityException("查询语句解析失败: " + e.getMessage());
        }
        Set<String> tables = extractTables(statement);
        SqlGuardContext context = new SqlGuardContext(cleaned, statement, tables);
        for (SqlGuard guard : guards) {
            guard.check(context);
        }
        return cleaned;
    }

    private String stripTrailingSemicolon(String sql) {
        String result = sql;
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private Set<String> extractTables(Statement statement) {
        try {
            List<String> tableList = new TablesNamesFinder().getTableList(statement);
            return tableList.stream().map(String::toLowerCase).collect(Collectors.toCollection(HashSet::new));
        } catch (Exception e) {
            log.warn("SQL 表名解析失败，按空集处理", e);
            return new HashSet<>();
        }
    }
}
