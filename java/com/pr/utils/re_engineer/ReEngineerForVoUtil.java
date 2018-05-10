package com.pr.utils.re_engineer;

import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.*;
import java.util.*;


/**
 * 数据库逆向工程生成VO类
 */
public class ReEngineerForVoUtil {

    private static final List<String> EXCLUDE_TYPES = Arrays.asList(new String[]{"BIT", "BLOB", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB", "BINARY", "VARBINARY"});

    private static final String GETTER_TEMPLATE = "\tpublic ${type} get${cname}() {\n" + "\t\treturn ${name};\n" + "\t}\n";
    private static final String SETTER_TEMPLATE = "\tpublic void set${cname}(${type} ${name}) {\n" + "\t\tthis.${name} = ${name};\n" + "\t}\n";

    // 配置文件名
    private static final String CONFIG_FILE_NAME = "re-engineer.xml";

    // 配置信息
    private Configuration config;

    // 数据库中实体的信息
    private List<EntityInfo> entities;

    public static void main(String... args) {
        new ReEngineerForVoUtil().create();
    }

    public void create() {
        long startTime = System.currentTimeMillis();
        System.out.println("ReEngineer for VO start...");

        // 加载配置信息
        loadConfiguration();

        // 读取数据库元数据
        System.out.println("Reading database meta data...");
        getEntityInfo();


        System.out.println("Writing file(s) to " + config.packagePath);
        // 确保存储文件的文件夹存在
        File folder = new File(config.packagePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        for (EntityInfo e : entities) {
            createFile(e, buildEntityFile(e));
        }

        System.out.println(entities.size() + " VO Class file(s) created in " + (System.currentTimeMillis() - startTime) + " ms.");
    }

    /**
     * 加载配置信息
     */
    private void loadConfiguration() {
        try {
            JAXBContext  context      = JAXBContext.newInstance(Configuration.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            String       rootPath;

            if (ClassLoader.getSystemResource(".") != null) {
                rootPath = ClassLoader.getSystemResource(".").getPath();
            } else {
                rootPath = System.getProperty("user.dir");
            }

            File configFile = new File(rootPath, CONFIG_FILE_NAME);
            if (!configFile.exists()) {
                throw new RuntimeException("找不到配置文件: " + configFile.getAbsolutePath());
            }

            System.out.println("Reading configuration: " + configFile.getAbsolutePath() + " ...");

            config = (Configuration) unmarshaller.unmarshal(configFile);

            if (StringUtils.isBlank(config.packagePath)) {
                config.packagePath = rootPath + File.separator + config.packageName.replace(".", File.separator);
            } else {
                config.packagePath = config.packagePath + File.separator + config.packageName.replace(".", File.separator);
            }
        } catch (JAXBException e) {
            throw new RuntimeException("读取配置文件失败: " + CONFIG_FILE_NAME, e);
        }
    }

    /**
     * 读取数据库元数据
     */
    private void getEntityInfo() {
        Connection conn     = null;
        Statement  stmt     = null;
        ResultSet  rsEntity = null;
        try {
            Class.forName(config.driver);
            conn = DriverManager.getConnection(config.url, config.userName, config.password);
            if (conn == null) throw new RuntimeException("连接数据库失败");
            DatabaseMetaData databaseMetaData = conn.getMetaData();
            ResultSet        rsEntities       = databaseMetaData.getTables(null, null, StringUtils.trimToNull(config.tableNamePattern), new String[]{"TABLE", "VIEW"});

            stmt = conn.createStatement();
            entities = new ArrayList<>();
            while (rsEntities.next()) {
                EntityInfo entity    = new EntityInfo();
                String     tableName = rsEntities.getString("TABLE_NAME");
                entity.name = config.prefix + StringUtils.capitalize(FieldNameConverter.lineToHump(tableName)) + config.suffix;
                entities.add(entity);

                // 读取列信息
                ResultSetMetaData rsEntityMetaData = stmt.executeQuery("select * from `" + tableName + "` limit 0, 0;").getMetaData();
                for (int i = 1, size = rsEntityMetaData.getColumnCount(); i <= size; i++) {
                    ColumnInfo col = new ColumnInfo();
                    col.name = FieldNameConverter.lineToHump(rsEntityMetaData.getColumnName(i));
                    col.sqlType = rsEntityMetaData.getColumnTypeName(i);
                    col.javaType = rsEntityMetaData.getColumnClassName(i);
                    col.length = rsEntityMetaData.getColumnDisplaySize(i);
                    typeTransfer(col);
                    col.javaTypeShort = col.javaType.substring(col.javaType.lastIndexOf('.') + 1);
                    entity.columns.add(col);
                }
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("读取数据库信息失败", e);
        } finally {
            if (rsEntity != null) {
                try {
                    rsEntity.close();
                } catch (SQLException e) {
                } finally {
                    try {
                        stmt.close();
                    } catch (SQLException e) {
                    } finally {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                        }
                    }
                }
            }
        }
    }

    /**
     * 对列的数据类型进行转换
     */
    private void typeTransfer(ColumnInfo col) {
        if (java.sql.Timestamp.class.getName().equals(col.javaType) || java.sql.Date.class.getName().equals(col.javaType)) {
            // 将Timestamp和sql.Date映射为 java.util.Date
            col.javaType = java.util.Date.class.getName();
        } else if (EXCLUDE_TYPES.contains(col.sqlType) && col.javaType.equals("[B")) {
            col.javaType = "java.lang.Character[]";
        }
    }

    /**
     * 构建单个实体的VO类文件
     */
    private String buildEntityFile(EntityInfo entity) {
        StringBuilder fileText = new StringBuilder();

        // 添加包声明
        if (StringUtils.isNotBlank(config.packageName)) {
            fileText.append("package " + config.packageName + ";\n\n");
        }

        // 添加import部分
        fileText.append(createImportsSnippet(entity));

        fileText.append("\n\n");

        // 添加类定义部分
        fileText.append("public class ").append(entity.name).append(" {\n");

        List<String> propertyDeclares = new ArrayList<>();  // 属性声明部分
        List<String> getterAndSetter  = new ArrayList<>();   // getter, setter方法部分

        // 处理每一个属性
        for (ColumnInfo col : entity.columns) {
            propertyDeclares.add(createPropertyDeclareSnippet(col));
            getterAndSetter.add(createGetterAndSetterSnippet(col));
        }

        // 添加属性声明部分
        fileText.append(StringUtils.join(propertyDeclares, "\n"));
        fileText.append("\n\n");

        // 添加getter, setter部分
        fileText.append(StringUtils.join(getterAndSetter, ""));

        // 结束类定义部分
        fileText.append("}");

        return fileText.toString();
    }

    /**
     * 构建 import 部分
     */
    private String createImportsSnippet(EntityInfo entity) {
        Set<String> javaClasses = new HashSet<>();
        for (ColumnInfo c : entity.columns) {
            javaClasses.add("import " + c.javaType + ";");
        }
        return StringUtils.join(javaClasses, "\n");
    }

    /**
     * 构建单个属性的声明
     */
    private String createPropertyDeclareSnippet(ColumnInfo column) {
        return "    private " + column.javaTypeShort + " " + column.name + ";";
    }


    /**
     * 构建一个属性的 getter, setter
     */
    private String createGetterAndSetterSnippet(ColumnInfo column) {
        return createGetterSnippet(column) + createSetterSnippet(column);
    }

    /**
     * 构建一个属性的 getter
     */

    private String createGetterSnippet(ColumnInfo column) {
        return GETTER_TEMPLATE.replace("${type}", column.javaTypeShort).replace("${name}", column.name).replace("${cname}", StringUtils.capitalize(column.name));
    }

    /**
     * 构建一个属性的setter
     */
    private String createSetterSnippet(ColumnInfo column) {
        return SETTER_TEMPLATE.replace("${type}", column.javaTypeShort).replace("${name}", column.name).replace("${cname}", StringUtils.capitalize(column.name));
    }


    /**
     * 创建VO类文件
     *
     * @param fileContent
     */
    private void createFile(EntityInfo entity, String fileContent) {
        try {
            File file = new File(config.packagePath, entity.name + ".java");
            if (!file.exists()) file.createNewFile();
            OutputStream out = new FileOutputStream(file);
            byte[]       b   = fileContent.getBytes();
            out.write(b);
            out.flush();
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("写文件失败!", e);
        }
    }

    /**
     * 配置信息
     */
    @XmlRootElement(name = "configuration")
    private static class Configuration {
        @XmlElement(name = "driver")
        private String driver;                  // 数据库驱动名
        @XmlElement(name = "url")
        private String url;                     // 数据库URL
        @XmlElement(name = "userName")
        private String userName;                // 登录名
        @XmlElement(name = "password")
        private String password;                // 密码
        @XmlElement(name = "tableNamePattern")
        private String tableNamePattern;        // 要映射的表或视图的名称匹配模式
        @XmlElement(name = "packageName")
        private String packageName;             // 包名
        @XmlElement(name = "prefix")
        private String prefix;                  // VO类名前缀
        @XmlElement(name = "suffix")
        private String suffix;                  // VO类名后缀
        @XmlElement(name = "packagePath")
        private String packagePath;            // 存放VO类文件的物理路径
    }

    /**
     * 数据库实体的元数据信息
     */
    private static class EntityInfo {
        private String           name;
        private List<ColumnInfo> columns = new ArrayList<>();
    }

    /**
     * 列信息
     */
    private static class ColumnInfo {
        private String name;            // 列名
        private String sqlType;         // 列的数据类型(数据库类型, 如: INT)
        private String javaType;        // 列的数据类型(如: java.lang.Integer)
        private String javaTypeShort;   // 列的数据库类型(如: Integer)
        private int    length;          // 长度
    }
}
