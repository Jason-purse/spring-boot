package club.smileboy.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Resource;
import sun.misc.URLClassPath;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author FLJ
 * @date 2022/5/25
 * @time 16:08
 * @description URL test
 * <p>
 * <p>
 * 框架中 通过URL 能够加载世界上任意一个位置的代码或者资源 ... 这就是为什么继承URLClassLoader的原因 ...
 */
public class URLTests {
	public static void main(String[] args) throws MalformedURLException {

		URL url = new URL("https://www.baidu.com/a");
		System.out.println(url.getPath());

	}

	@TempDir
	private File temp;

	/**
	 * 那么 URLClassLoader 使用URLPath 进行资源查找
	 *
	 * 完成测试之后 删除资源信息 ...
	 */
	@Test
	public void test() throws IOException {


		File checkDir = new File(temp, "exploded");
		if(!checkDir.exists()) {
			checkDir.mkdirs();
			checkDir.deleteOnExit();
		}

		String sourcePath = "src/test/resources";
		Path path = Paths.get(sourcePath);
		Path fooJar = checkDir.toPath().resolve("foo.jar");
		fooJar.toFile().deleteOnExit();
		Path barJar = checkDir.toPath().resolve("bar.jar");
		barJar.toFile().deleteOnExit();
		Path bazJar = checkDir.toPath().resolve("baz.jar");
		barJar.toFile().deleteOnExit();
		Files.copy(path.resolve("exploded/foo.jar"),fooJar, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(path.resolve("exploded/bar.jar"),barJar, StandardCopyOption.REPLACE_EXISTING);
		Files.copy(path.resolve("exploded/baz.jar"),bazJar, StandardCopyOption.REPLACE_EXISTING);


		Path resolve = checkDir.toPath().resolve("BOOT-INF/classes");
		File file = resolve.toFile();
		if(!file.exists()) {
			file.mkdirs();
			file.deleteOnExit();
		}

		Path explodedsample = resolve.resolve("explodedsample");
		File file1 = explodedsample.toFile();
		if(!file1.exists()) {
			file1.mkdir();
			file1.deleteOnExit();
		}

		Path classes = explodedsample.resolve("ExampleClass.class");
		Files.copy(path.resolve("exploded/ExampleClass"),classes,StandardCopyOption.REPLACE_EXISTING);
		classes.toFile().deleteOnExit();


		// 现在能够体会 findResource 并不保证顺序,随便拿到一个直接返回 ...
		// findResources 直接 返回所有loader中发现的 ..
		URLClassPath urlClassPath = new URLClassPath(new URL[]{
				file.toURI().toURL(),
				fooJar.toUri().toURL(),
				barJar.toUri().toURL(),
				bazJar.toUri().toURL()
		});


		Resource resource = urlClassPath.getResource("explodedsample/ExampleClass.class");




		// 关闭这些加载器,否则无法正常删除文件
		urlClassPath.closeLoaders();
		assert resource != null : "resource 不应该为空";

		System.out.println(String.format("printf resource %s", resource.getURL()));

	}
}
