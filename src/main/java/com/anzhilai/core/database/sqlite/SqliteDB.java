package com.anzhilai.core.database.sqlite;

import com.anzhilai.core.base.BaseModel;
import com.anzhilai.core.base.BaseQuery;
import com.anzhilai.core.base.XColumn;
import com.anzhilai.core.base.XIndex;
import com.anzhilai.core.database.*;
import com.anzhilai.core.framework.GlobalValues;
import com.anzhilai.core.toolkit.DateUtil;
import com.anzhilai.core.toolkit.LockUtil;
import com.anzhilai.core.toolkit.StrUtil;
import com.anzhilai.core.toolkit.TypeConvert;
import java.io.File;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SqliteDB extends DBBase {
    public String dbPath = null;
    public static final String MEMORY_DB_PATH = ":memory:";//内存数据库

    public SqliteDB(String path) {
        createIdIndex = false;
        try {
            if (MEMORY_DB_PATH.equals(path)) {
                this.dbPath = path;
            } else {
                this.dbPath = new File(path).getAbsoluteFile().getPath();
            }
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void beginTransaction() throws SQLException {
        if (this.connection == null) {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbPath);
        }
        super.beginTransaction();
        this.lockDb();
    }

    public void close() {
        super.close();
        this.unLockDb();
    }

    public String getLockKey() {
        String lockKey = StrUtil.isEmpty(this.dbPath) ? "" : this.dbPath;
        return lockKey;
    }

    public void lockDb() {
        LockUtil.Lock(getLockKey());
    }

    public void unLockDb() {
        LockUtil.UnLock(getLockKey());
    }

    public void handleDataTable(DataTable dataTable) {
        if (dataTable.Data.size() > 0) {
            List<String> listKey = new ArrayList<>();//时间转换
            for (String col : dataTable.DataSchema.keySet()) {
                if (Date.class.isAssignableFrom(dataTable.DataSchema.get(col))) {
                    listKey.add(col);
                }
            }
            for (Map<String, Object> row : dataTable.Data) {
                for (String col : listKey) {
                    Object value = row.get(col);
                    if (value != null) {
                        row.put(col, new Date(TypeConvert.ToLong(value)));
                    }
                }
            }
        }
    }

    //获取索引名称
    public String GetTableIndexName(String tableName, String... colName) {
        String ret = "index_" + tableName + "_";
        for (String str : colName) {
            ret += str;
        }
        return ret;
    }

    //查询索引列表
    public String GetTableIndex(String tableName) {
        return "select name as Key_name from sqlite_master where type = 'index' AND tbl_name = '" + tableName + "'";
    }

    public String GetLimitString(BaseQuery pageInfo, String sql) {
        return sql + " LIMIT " + pageInfo.PageIndex + "," + pageInfo.PageSize;
    }

    public String getLimitString(String query, boolean hasOffset) {
        return new StringBuffer(query.length() + 20).append(query).append(
                hasOffset ? " limit ? offset ?" : " limit ?").toString();
    }


    @Override
    public DataTable GetTables() throws SQLException {
        String sql = "select name from sqlite_master where type='table' order by name";
        DataTable dt = SqlExe.ListSql(new SqlInfo().Append(sql), null);
        for (Map<String, Object> row : dt.Data) {
            String 表名 = TypeConvert.ToString(row.get(row.keySet().toArray()[0]));
            row.put("表名", 表名);
            row.put(BaseModel.F_id, 表名);
        }
        return dt;
    }

    protected void ExeSql(String sql, Object... params) throws SQLException {
        DBSession.getSession().doWork(conn -> {
            PreparedStatement statement = conn.getConnection().prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i, params[i]);
                }
            }
            statement.execute();
        });
    }

    //sqlite 自己处理表的修改
    @Override
    public void AlterTable(Class clazz, String tableName, DataTable dt) throws SQLException {
        String sqlindex = GetTableIndex(tableName);
        Field[] fields = clazz.getFields();
        boolean reset = false;
        String columns = "";
        for (Field field : fields) {
            XColumn xc = field.getAnnotation(XColumn.class);
            String columnName = field.getName();
            if (xc != null) {
                if (StrUtil.isNotEmpty(xc.name())) {
                    columnName = xc.name();
                }
                boolean has = false;
                boolean sametype = false;
                for (String col : dt.DataSchema.keySet()) {
                    if (columnName.equals(col)) {
                        columns += "," + getQuote(columnName);
                        has = true;
                        if (getDbType(field.getType(), xc).equals(getDbType(dt.DataSchema.get(col), xc))) {
                            sametype = true;
                        } else {
                            sametype = false;
                        }
                    }
                }
                if (!has || !sametype) {
                    reset = true;
                }
            }
        }
        DataTable dtindex = SqlExe.ListSql(new SqlInfo().Append(sqlindex), null);
        if (reset) {//需要重新初始化表
            String oldTableName = "_" + tableName + "_old_" + DateUtil.GetDateString(new Date(), "yyyyMMdd_hhmmss");
            String sql = "ALTER TABLE " + getQuote(tableName) + " RENAME TO " + getQuote(oldTableName);
            ExeSql(sql);
            for (Map mi : dtindex.Data) {
                String indexName = TypeConvert.ToString(mi.get("Key_name"));
                if (indexName.startsWith("index_")) {
                    sql = "DROP INDEX " + getQuote(indexName);
                    ExeSql(sql);
                }
            }
            CreateTable(clazz, tableName);
            //合并数据
            columns = columns.replaceFirst(",", "");
            sql = "INSERT INTO " + getQuote(tableName) + "(" + columns + ") SELECT " + columns + " FROM " + getQuote(oldTableName);
            ExeSql(sql);
        } else {
            for (Field field : fields) {
                XColumn xc = field.getAnnotation(XColumn.class);
                String columnName = field.getName();
                if (xc != null) {
                    if (StrUtil.isNotEmpty(xc.name())) {
                        columnName = xc.name();
                    }
                    XIndex xi = field.getAnnotation(XIndex.class);
                    if (xi != null) {
                        String icolumns = StrUtil.join(xi.columns());
                        if (StrUtil.isEmpty(icolumns)) {
                            icolumns = columnName;
                        }
                        String ikey = icolumns + "_index";
                        String indexName = GetTableIndexName(tableName, icolumns);
                        if (StrUtil.isNotEmpty(indexName)) {
                            ikey = indexName;
                        }
                        boolean has = false;
                        for (Map mi : dtindex.Data) {
                            String iname = TypeConvert.ToString(mi.get("Key_name"));
                            if (ikey.equals(iname)) {
                                has = true;
                            }
                        }
                        if (!has) {//创建索引
                            StringBuilder sindex = new StringBuilder();
                            // create index 索引名 using hash on 表名(列名);
                            if (xi.unique()) {
                                sindex.append(" create UNIQUE index ");
                            } else {
                                sindex.append(" create index ");
                            }
                            sindex.append(ikey);
                            sindex.append(" on ").append(tableName).append("(").append(icolumns).append(")");
                            ExeSql(sindex.toString());
                        }
                    }
                }
            }
        }
    }

    //备份数据库
    public void BackupByFile(File file) throws SQLException {
        try (Statement statement = this.getConnection().createStatement()) {
            statement.execute("backup to " + file.getPath());
        } catch (Exception e) {
            throw e;
        }
    }

    //恢复数据库
    public void RestoreByFile(File file) throws SQLException {
        if (file.exists()) {
            try (Statement statement = this.getConnection().createStatement()) {
                statement.execute("restore from " + file.getPath());
            } catch (Exception e) {
                throw e;
            }
        }
    }


    public void RegisterTypes(){
        registerColumnType(Types.BIT, "integer");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.FLOAT, "double");
        registerColumnType(Types.REAL, "real");
        registerColumnType(Types.DOUBLE, "double");
        registerColumnType(Types.NUMERIC, "numeric");
        registerColumnType(Types.DECIMAL, "decimal");
        registerColumnType(Types.CHAR, "char");
        registerColumnType(Types.VARCHAR, "varchar");
        registerColumnType(Types.LONGVARCHAR, "longvarchar");
        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "timestamp");
        registerColumnType(Types.BINARY, "blob");
        registerColumnType(Types.VARBINARY, "blob");
        registerColumnType(Types.LONGVARBINARY, "blob");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.CLOB, "clob");
        registerColumnType(Types.BOOLEAN, "integer");
    }
}
