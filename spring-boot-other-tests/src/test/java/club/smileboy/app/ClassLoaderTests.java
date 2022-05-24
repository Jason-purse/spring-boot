package club.smileboy.app;

import org.junit.jupiter.api.Test;
import sun.misc.Launcher;

import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;

/**
 * 类加载器的测试
 *
 * 想要知道的东西是:
 *
 * 为什么大多数的类加载器  都实现URLClassLoader
 * spring boot 为什么定义一个类加载器  叫做Launched .. ClassLoader
 * 它想要干什么,它在模仿什么 ?
 *
 * 其次 {@link org.springframework.boot.loader.jar.Handler} 为什么 一定叫Handler 这个名字?  并且需要放置在以 .jar 结尾的package 中 ..
 */
public class ClassLoaderTests {
	public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
		//FileTypeTests.main(null);
		FileTests.main(null);
	}

	/**
	 * Launcher 中有两个私有的类加载器实现
	 *
	 *
	 * 一个扩展类加载器,一个App类加载器
	 *
	 * {@link Launcher.ExtClassLoader} 根据这个jvm 属性 -> java.ext.dirs的 设定 进行额外的类加载 ...
	 *
	 * 但是许多书籍(例如java  core 技术中说 应该不使用此目录进行类加载), 降低了跨平台特性,其次好像是有一些安全问题 ...
	 *
	 * {@link Launcher.AppClassLoader} 根据这个jvm 属性 -> java.class.path 的设定进行类加载
	 *
	 * 本质上它们都是通过URLClassPath 进行 class / resource 加载 ..
	 *
	 * 所以这解决了第一个问题 为什么大家都实现URLClassLoader(其次 它的实现类似于一个Adapter的形式,我们只需要覆盖我们感兴趣的部分)
	 *
	 * //这里所说的 只针对java 8
	 *
	 *
	 *
	 * 其次,{@link Launcher.Factory} 是一个{@link java.net.URLStreamHandler}
	 * 这个实现子类明确了一个方式,前缀  + 包名 + Handler 指定一个特定的URLStreamHandler
	 * 所以 可以猜测的是 SpringBoot 沿用了jdk的写法, 模仿它做了这样的一个约定 ....
	 * 所以Springboot 一定有一个Launcher ...
	 */
	@Test
	public  void classLoaderTests() {
		//Launcher
		Launcher launcher = Launcher.getLauncher();
	}

	/**
	 * 文件 难道路径可以直接指定一个 网络地址 ??
	 */
	public static class FileTests {


		public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
			// 只能够读取本地文件 ...
			System.out.println(new File("http://jianyue-face.oss-cn-shanghai.aliyuncs.com/files/drown-guard/images/26AFACF68AB1420B902A0C6E57BF8572.jpeg")
					.exists());
			// file .

			class A {
				private A() {

				}
			}

			// 这是一个逃狱方法,让一些对象  可以访问 ..
			final Constructor<A> declaredConstructor = A.class.getDeclaredConstructor();
			AccessibleObject.setAccessible(new AccessibleObject[] {declaredConstructor},true);


			System.out.println(declaredConstructor.isAccessible());
			final A a = declaredConstructor.newInstance();
			System.out.println(a);
		}
	}

	/**
	 * 文件类型测试
	 * 应该需要public 可见才可以 ...
	 */
	public static class FileTypeTests {
		public static void main(String[] args) {
			System.out.println(JarURLConnection.getFileNameMap());
		}
	}
}
