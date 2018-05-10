# 数据库逆向工程工具
根据数据库模式逆向生成相应的Java VO.

## 依赖
* commons-lang3
```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.7</version>
</dependency>
```

* 数据库驱动
目标数据库的JDBC驱动. 
例如, 若数据库为MySQL则添加如下依赖
```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>5.1.22</version>
</dependency>
```

## 使用方法
### IDE外使用
* 配置re-engineer.xml
从jar包中将re-genineer.xml文件拷贝至jar文件同目录, 根据注释填写相应的内容.

* 运行本工具
java -jar re-engineer-1.0.1.jar

### 在IDE中使用
* 配置re-engineer.xml
从jar包中将re-genineer.xml文件拷贝至Java工程src根目录, 根据注释填写相应的内容.

* 运行本工具
可创建一个带程序入口的类, 例如:
```java
import com.pr.utils.re_engineer.ReEngineerForVoUtil;

public class VOCreator {
    public static void main (String... args) {
        new ReEngineerForVoUtil().create();
    }
}
```


