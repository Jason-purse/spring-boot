package club.smileboy.app.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;

/**
 * 默认的一个任务 ...
 */
public abstract class MyDefaultTask extends DefaultTask {
	@Nested
	abstract Property<JavaLauncher> getLauncher();

	@Inject
	MyDefaultTask() {

		// 本身 Java 插件能够支持java 工具链 .. .你能够进行配置或者获取 ..

		JavaToolchainSpec toolchain = this.getProject().getExtensions().getByType(JavaPluginExtension.class)
				.getToolchain();

		// 然后根据注册一个
		Provider<JavaLauncher> javaLauncherProvider = getProject().getExtensions().getByType(JavaToolchainService.class).launcherFor(toolchain);
		// 于是这是一个约定 ..
		// 当没有此类型的任务没有设定时
		// 默认取用这里配置的约定 ...
		getLauncher().convention(javaLauncherProvider);
	}
}
