package club.smileboy.app;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
   * @author FLJ
   * @date 2022/6/6
   * @time 12:50
   * @Description 我的自定义测试插件 ...(可以是一个脚本,因为build.gradle 相关的.gradle 脚本没有代码提示,通过应用插件的方式,配置project)
   */
public class MyCustomPlugin implements Plugin<Project> {

	@Override
	public void apply(Project target) {
		target.apply();
	}
}

